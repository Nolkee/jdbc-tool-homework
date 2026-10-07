# JDBC工具的设计与开发

Java 应用开发框架课程作业，使用 Maven 构建、Git 管理版本，通过反射和 JDBC 实现学生表、学院表的增删改查。

`JDBCTool` 包含五个方法：`resultSetToList`、`save`、`update`、`delete`、`getOneById`。

## 运行

环境：JDK 11 及以上、Maven 3.6.3 及以上。

```bash
mvn clean package
java -jar target/jdbc-tool-homework-1.0.0.jar
```

默认使用 H2 数据库，自动创建 `DateTest` 数据库和两张表，无需安装数据库服务器。项目也提供 MySQL 配置和建表脚本，当前运行验证使用 H2。

运行测试：`mvn test`。

## 主要文件

- `src/main/java/edu/homework/jdbc/util/JDBCTool.java`：JDBC 工具类。
- `src/main/java/edu/homework/jdbc/entity/`：学生、学院实体类。
- `src/main/java/edu/homework/jdbc/Demo.java`：增删改查演示。
- `sql/mysql-init.sql`：MySQL 建库建表脚本。

## 说明

由GPT-6.1 Sol辅助完成
