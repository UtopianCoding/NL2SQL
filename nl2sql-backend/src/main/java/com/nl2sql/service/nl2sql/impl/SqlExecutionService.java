package com.nl2sql.service.nl2sql.impl;

import com.nl2sql.model.entity.DataSource;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SqlExecutionService {

    @Value("${nl2sql.query.max-result-rows}")
    private int maxResultRows;

    @Value("${nl2sql.query.timeout-seconds}")
    private int queryTimeout;

    private static final int MAX_CONNECTION_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public void validateSql(String sql) {
        validateSqlWithParser(sql);

        String upperSql = sql.toUpperCase().trim();
        if (!upperSql.startsWith("SELECT")) {
            throw new RuntimeException("只允许执行SELECT查询");
        }

        List<String> dangerousKeywords = Arrays.asList(
                "DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE",
                "ALTER", "CREATE", "GRANT", "REVOKE", "EXEC", "EXECUTE"
        );
        for (String keyword : dangerousKeywords) {
            String pattern = "(?<![A-Z_])" + keyword + "(?![A-Z_])";
            if (java.util.regex.Pattern.compile(pattern).matcher(upperSql).find()) {
                throw new RuntimeException("SQL包含不允许的关键词: " + keyword);
            }
        }
    }

    public List<Map<String, Object>> executeSelect(DataSource ds, String sql) {
        String jdbcUrl = buildJdbcUrl(ds);
        List<Map<String, Object>> results = new ArrayList<>();

        SQLException lastException = null;
        
        // 连接重试机制
        for (int retry = 0; retry < MAX_CONNECTION_RETRIES; retry++) {
            try (Connection conn = DriverManager.getConnection(jdbcUrl, ds.getUsername(), ds.getPassword());
                 java.sql.Statement stmt = conn.createStatement()) {

                stmt.setQueryTimeout(queryTimeout);
                stmt.setMaxRows(maxResultRows);

                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnLabel(i);
                            Object value = rs.getObject(i);
                            row.put(columnName, value);
                        }
                        results.add(row);
                    }
                }
                
                // 执行成功，返回结果
                return results;
                
            } catch (SQLException e) {
                lastException = e;
                String errorMsg = e.getMessage();
                
                // 如果是连接数过多错误，等待后重试
                if (errorMsg != null && errorMsg.contains("Too many connections")) {
                    log.warn("数据库连接数已满，第{}次重试...", retry + 1);
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (retry + 1)); // 递增延迟
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("SQL执行被中断", ie);
                    }
                } else {
                    // 其他SQL错误直接抛出
                    log.error("SQL执行失败: {}", errorMsg);
                    throw new RuntimeException("SQL执行失败: " + errorMsg);
                }
            }
        }
        
        // 所有重试都失败
        log.error("SQL执行失败，已重试{}次: {}", MAX_CONNECTION_RETRIES, lastException.getMessage());
        throw new RuntimeException("SQL执行失败（已重试" + MAX_CONNECTION_RETRIES + "次）: " + lastException.getMessage());
    }

    private void validateSqlWithParser(String sql) {
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements.getStatements() == null || statements.getStatements().size() != 1) {
                throw new RuntimeException("只允许单条SELECT查询语句");
            }
            Statement statement = statements.getStatements().get(0);
            if (!(statement instanceof Select)) {
                throw new RuntimeException("只允许执行SELECT查询");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("SQL语法解析失败: " + e.getMessage(), e);
        }
    }

    private String buildJdbcUrl(DataSource ds) {
        return switch (ds.getType().toLowerCase()) {
            case "mysql" -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                    ds.getHost(), ds.getPort(), ds.getDatabaseName());
            case "postgresql", "pg" -> String.format("jdbc:postgresql://%s:%d/%s",
                    ds.getHost(), ds.getPort(), ds.getDatabaseName());
            case "oracle" -> String.format("jdbc:oracle:thin:@%s:%d:%s",
                    ds.getHost(), ds.getPort(), ds.getDatabaseName());
            case "sqlserver", "mssql" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false",
                    ds.getHost(), ds.getPort(), ds.getDatabaseName());
            case "oceanbase" -> String.format("jdbc:mysql://%s:%d/%s", ds.getHost(), ds.getPort(), ds.getDatabaseName());
            default -> throw new RuntimeException("不支持的数据库类型: " + ds.getType());
        };
    }
}
