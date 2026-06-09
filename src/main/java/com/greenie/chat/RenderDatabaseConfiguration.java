package com.greenie.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name = "DATABASE_URL")
public class RenderDatabaseConfiguration {

    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${DATABASE_URL}") String databaseUrl,
            @Value("${RENDER_DB_REGION:frankfurt}") String region) {
        URI uri = URI.create(databaseUrl.replaceFirst("^postgres(ql)?://", "postgresql://"));

        String username = null;
        String password = null;
        if (uri.getUserInfo() != null) {
            String[] userInfo = uri.getUserInfo().split(":", 2);
            username = urlDecode(userInfo[0]);
            if (userInfo.length > 1) {
                password = urlDecode(userInfo[1]);
            }
        }

        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        if (host != null && host.startsWith("dpg-") && !host.contains(".")) {
            host = host + "." + region + "-postgres.render.com";
            port = 5432;
        }

        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + uri.getPath();
        if (uri.getQuery() != null) {
            jdbcUrl += "?" + uri.getQuery();
        } else if (host != null && host.contains("render.com")) {
            jdbcUrl += "?sslmode=require";
        }

        return DataSourceBuilder.create()
                .driverClassName("org.postgresql.Driver")
                .url(jdbcUrl)
                .username(username)
                .password(password)
                .build();
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
