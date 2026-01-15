/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.clustering.testcontainers;

import java.util.List;

import org.jboss.as.test.shared.observability.containers.BaseContainer;

/**
 * PostgreSQL container for clustering tests such as JDBC_PING2 discovery testing.
 *
 * @author Radoslav Husar
 */
public class PostgreSQLContainer extends BaseContainer<PostgreSQLContainer> {

    private static final String CONTAINER_NAME = "postgresql";
    private static final String IMAGE_NAME = "postgres";
    private static final String IMAGE_VERSION = "17";
    private static final int POSTGRESQL_PORT = 5432;

    public static final String DATABASE_NAME = "jgroups-ping-db";
    public static final String USERNAME = "jgroups-user";
    public static final String PASSWORD = "jgroups-password";


    public PostgreSQLContainer() {
        super(CONTAINER_NAME, IMAGE_NAME, IMAGE_VERSION, List.of(POSTGRESQL_PORT));
    }

    @Override
    protected void configure() {
        withEnv("POSTGRES_DB", DATABASE_NAME);
        withEnv("POSTGRES_USER", USERNAME);
        withEnv("POSTGRES_PASSWORD", PASSWORD);
    }

    /**
     * Returns the JDBC URL for connecting to the PostgreSQL database.
     *
     * @return the JDBC connection URL
     */
    public String getJdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s", getHost(), getMappedPort(POSTGRESQL_PORT), DATABASE_NAME);
    }

    @Override
    public void start() {
        super.start();

        debugLog("PostgreSQL port: " + getMappedPort(POSTGRESQL_PORT));
    }
}
