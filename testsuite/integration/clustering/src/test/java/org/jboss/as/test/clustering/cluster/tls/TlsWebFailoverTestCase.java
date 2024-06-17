package org.jboss.as.test.clustering.cluster.tls;

import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.clustering.cluster.web.CoarseWebFailoverTestCase;

/**
 * Variation of the 'default' {@link CoarseWebFailoverTestCase} that uses TLS-secured JGroups communication channel.
 *
 * @author Radoslav Husar
 */
@ServerSetup(CoarseWebFailoverTestCase.ServerSetup.class)
public class TlsWebFailoverTestCase extends CoarseWebFailoverTestCase {
}
