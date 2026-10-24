# Surefire "system property is configured twice" warnings in the testsuite

## Problem

Integration testsuite surefire executions print a block of warnings like this, e.g. for `basic-integration-ldap.surefire` in `testsuite/integration/basic`:

```
[WARNING] The system property java.io.tmpdir is configured twice! The property appears in <argLine/> and any of <systemPropertyVariables/>, <systemProperties/> or user property.
[WARNING] The system property jboss.dist is configured twice! ...
[WARNING] The system property jbossas.ts.submodule.dir is configured twice! ...
[WARNING] The system property jbossas.ts.integ.dir is configured twice! ...
[WARNING] The system property jbossas.ts.dir is configured twice! ...
[WARNING] The system property jbossas.project.dir is configured twice! ...
[WARNING] The system property org.jboss.ejb.client.wildfly-testsuite-hack is configured twice! ...
```

## Cause

Surefire (3.5.4, `AbstractSurefireMojo.setupProperties()`) builds the effective system properties from `<systemProperties>`, `<systemPropertyVariables>` and Maven session user properties.
For every key in that set it also checks whether the `argLine` contains `-D<key>=`, and warns if it does.

When a property is set both ways, the `systemPropertyVariables` value wins at runtime: surefire applies it inside the forked JVM after startup, overriding the `-D` from the `argLine`.

Every property in the warning was set both ways:

| Property | Set in `argLine` via | Also set in `systemPropertyVariables` |
|---|---|---|
| `jbossas.ts.submodule.dir`, `jbossas.ts.integ.dir`, `jbossas.ts.dir`, `jbossas.project.dir` | `${jvm.args.dirs}` in the `argLine` of `testsuite/integration/pom.xml` | `testsuite/integration/pom.xml`, same values |
| `jboss.dist` | `-Djboss.dist=${jboss.dist}` in `${surefire.system.args}` (`testsuite/pom.xml`, mirrored in `testsuite/integration/clustering/pom.xml`), and again via `${jvm.args.dirs}` | `testsuite/pom.xml` (pluginManagement) and `testsuite/integration/pom.xml`; many executions override it with their own dist, e.g. `${project.build.directory}/wildfly-ejb` |
| `java.io.tmpdir` | `-Djava.io.tmpdir=${basedir}/target` in `${jvm.args.dirs}` | the parent POM `org.jboss:jboss-parent:51`, which sets `<java.io.tmpdir>${project.build.directory}</java.io.tmpdir>`, i.e. the same directory |
| `org.jboss.ejb.client.wildfly-testsuite-hack` | `-Dorg.jboss.ejb.client.wildfly-testsuite-hack=true` in `${surefire.system.args}` | copied into many individual executions under the comment `EJB client library hack, see WFLY-4973` |

The `java.io.tmpdir` duplicate is not visible from grepping the WildFly POMs, because the `systemPropertyVariables` side comes from `jboss-parent`.

For executions that override `jboss.dist` (most of the per-layer executions in `testsuite/integration/basic/pom.xml`), the `argLine` value was not just redundant but wrong, and only harmless because it gets overridden.

## Fix

Keep `systemPropertyVariables` as the single source for each property seen by the test client JVM, and remove the extra copies from the other side.
The server JVM arguments (`server.jvm.args`) are not changed.

1. **`testsuite/integration/pom.xml`:** drop `${jvm.args.dirs}` from the surefire `argLine`.
   ```diff
   -<argLine>${surefire.system.args} ${surefire.memory.args} ${jvm.args.ip.client} ${jvm.args.timeouts} ${jvm.args.dirs}</argLine>
   +<argLine>${surefire.system.args} ${surefire.memory.args} ${jvm.args.ip.client} ${jvm.args.timeouts}</argLine>
   ```
   `jvm.args.dirs` is still used in `server.jvm.args`, so servers keep receiving these properties.

2. **`testsuite/pom.xml` and `testsuite/integration/clustering/pom.xml`:** remove `-Djboss.dist=${jboss.dist}` from `surefire.system.args`.
   Integration servers still get `jboss.dist` through `jvm.args.dirs`.
   Servers configured without `jvm.args.dirs` (domain, mixed-domain, iiop) no longer get it, which should be safe: only test-side code reads `jboss.dist` (e.g. `TestsuiteSanityTestCase`, `AppClientWrapper`, `AbstractParseAndMarshalModelsTestCase`, `ProvisioningConsistencyBaseTest`).

3. **Remove the duplicate `org.jboss.ejb.client.wildfly-testsuite-hack` entries (with their WFLY-4973 comment)** from `systemPropertyVariables` in:
   - `testsuite/integration/basic/pom.xml` (6 executions)
   - `testsuite/integration/multinode/pom.xml`
   - `testsuite/integration/manualmode/pom.xml`
   - `testsuite/integration/ws/pom.xml` (2 executions)
   - `testsuite/preview/basic/pom.xml` (2 executions)
   - `testsuite/preview/expansion/pom.xml` (2 executions)
   - `testsuite/preview/manualmode/pom.xml` (2 executions)

   `testsuite/integration/iiop/pom.xml` keeps its entry: it replaces the `argLine` with one that does not include `${surefire.system.args}`, so the `systemPropertyVariables` entry is the only one there.

Total: 10 files, +3/−35 lines.

### Behaviour change

In the test client JVM, `java.io.tmpdir` is now set by surefire after the JVM starts (from `jboss-parent`) rather than on the java command line.
This is how `jboss-parent` already sets it for the rest of the WildFly build, so no problems are expected.
Anything that reads `java.io.tmpdir` before surefire applies its properties would see the OS default instead.

## Verification

Ran `BasicFlatTestCase` in the `basic-integration-ldap.surefire` execution:

```
./mvnw -f testsuite/integration/basic/pom.xml surefire:test@basic-integration-ldap.surefire -Dtest=BasicFlatTestCase
```

| POM state | Warnings | Result |
|---|---|---|
| Original | all 7 | 4/4 tests pass |
| Steps 2 and 3, `-Djava.io.tmpdir` still in `argLine` | only `java.io.tmpdir` | 4/4 tests pass |
| Final (all steps) | none | not verified: the run clashed with a concurrently running clustering testsuite server on port 9990 and errored with `WFLYJCA0116` |

**The final version still needs a clean run** with no other WildFly server running.
A run with `-Dtest=DoesNotExist` does not reach surefire's property check, so it cannot be used to confirm the warnings are gone.

## Unrelated observation

The integration `argLine` adds `${surefire.memory.args}`, which `${surefire.system.args}` already includes.
Surefire does not warn about this because it is not a `-D` property, so it was left as is.
