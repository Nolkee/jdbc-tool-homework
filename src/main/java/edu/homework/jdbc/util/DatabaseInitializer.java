package edu.homework.jdbc.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** 创建演示所需的两张表，保留已有数据。 */
public final class DatabaseInitializer {
    private DatabaseInitializer() {
    }

    public static void initialize(Connection connection) throws SQLException {
        String schema;
        try (InputStream input = DatabaseInitializer.class.getResourceAsStream("/schema.sql")) {
            if (input == null) {
                throw new SQLException("找不到数据库建表文件：/schema.sql");
            }
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("读取数据库建表文件失败", e);
        }

        // 本项目建表文件只有两条普通 SQL，以分号分隔即可。
        try (Statement statement = connection.createStatement()) {
            for (String sql : schema.split(";")) {
                if (!sql.trim().isEmpty()) {
                    statement.execute(sql.trim());
                }
            }
        }
    }
}
