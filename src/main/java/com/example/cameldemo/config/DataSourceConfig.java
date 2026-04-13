package com.example.cameldemo.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    /**
     * Primary Oracle datasource. Named "oracleDataSource" so it can be referenced
     * directly from Camel routes via: jdbc:oracleDataSource
     */
    @Primary
    @Bean(name = "oracleDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.oracle")
    public DataSource oracleDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * Kinetica datasource. Named "kineticaDataSource" for use in Camel routes via:
     * jdbc:kineticaDataSource
     */
    @Bean(name = "kineticaDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.kinetica")
    public DataSource kineticaDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }
}
