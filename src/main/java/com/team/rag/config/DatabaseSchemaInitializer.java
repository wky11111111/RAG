package com.team.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

@Component
public class DatabaseSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureLargeTextColumns() {
        if (!isMySql()) {
            return;
        }
        alterIfNeeded("ALTER TABLE rag_document MODIFY COLUMN raw_text LONGTEXT NULL");
        alterIfNeeded("ALTER TABLE rag_chunk MODIFY COLUMN content LONGTEXT NOT NULL");
        alterIfNeeded("UPDATE rag_document SET source_kind = 'RAG' WHERE source_kind IS NULL OR source_kind = ''");
    }

    private boolean isMySql() {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase(Locale.ROOT).contains("mysql");
        } catch (SQLException ex) {
            log.warn("Unable to detect database product before schema compatibility check", ex);
            return false;
        }
    }

    private void alterIfNeeded(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (DataAccessException ex) {
            log.warn("Schema compatibility statement skipped: {}", ex.getMostSpecificCause().getMessage());
        }
    }
}
