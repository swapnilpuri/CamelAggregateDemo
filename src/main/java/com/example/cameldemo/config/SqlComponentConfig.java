package com.example.cameldemo.config;

import org.apache.camel.component.sql.SqlComponent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Registers the Camel SQL component as a Spring bean named "sql".
 *
 * Camel picks up any Spring bean whose name matches a component scheme as that
 * component's implementation.  Wiring oracleDataSource explicitly ensures the
 * sql: endpoints in filtered-query-routes.camel.xml read from Oracle even though
 * the application also has a kineticaDataSource in the context.
 *
 * This config is required because camel-sql-spring-boot-starter auto-configuration
 * uses the primary DataSource, which is oracleDataSource here, but the explicit
 * wiring makes the intent unambiguous and guards against future primary changes.
 */
@Configuration
public class SqlComponentConfig {

    @Bean(name = "sql")
    public SqlComponent sqlComponent(@Qualifier("oracleDataSource") DataSource oracleDataSource) {
        SqlComponent component = new SqlComponent();
        component.setDataSource(oracleDataSource);
        return component;
    }
}
