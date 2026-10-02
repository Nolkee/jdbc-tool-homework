package edu.homework.jdbc.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

/** 读取数据库配置，环境变量可覆盖配置文件中的连接信息。 */
public final class DatabaseConfig {
    private DatabaseConfig() {
    }

    public static Connection open(String mode) throws SQLException {
        String selectedMode = mode == null || mode.trim().isEmpty()
                ? "h2" : mode.trim().toLowerCase(Locale.ROOT);
        if (!"h2".equals(selectedMode) && !"mysql".equals(selectedMode)) {
            throw new IllegalArgumentException("数据库模式只支持 h2 或 mysql：" + mode);
        }

        String resource = "/db-" + selectedMode + ".properties";
        Properties properties = new Properties();
        try (InputStream input = DatabaseConfig.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new SQLException("找不到数据库配置文件：" + resource);
            }
            properties.load(input);
        } catch (IOException e) {
            throw new SQLException("读取数据库配置文件失败", e);
        }

        String url = environmentOrProperty("JDBC_URL", properties, "url");
        String user = environmentOrProperty("JDBC_USER", properties, "user");
        String password = environmentOrProperty("JDBC_PASSWORD", properties, "password");
        return DriverManager.getConnection(url, user, password);
    }

    private static String environmentOrProperty(String environmentName,
                                                Properties properties, String propertyName) {
        String value = System.getenv(environmentName);
        // 空密码也是有效配置，不能使用 isEmpty() 判断是否覆盖。
        return value != null ? value : properties.getProperty(propertyName, "");
    }
}
