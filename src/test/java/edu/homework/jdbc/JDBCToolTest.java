package edu.homework.jdbc;

import edu.homework.jdbc.annotation.Table;
import edu.homework.jdbc.entity.College;
import edu.homework.jdbc.entity.Student;
import edu.homework.jdbc.util.JDBCTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 每个测试使用独立的真实 H2 内存数据库，验证工具对数据库产生的实际影响。
 */
class JDBCToolTest {
    private Connection connection;

    @BeforeEach
    void createDatabase() throws SQLException, IOException {
        String url = "jdbc:h2:mem:test_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        connection = DriverManager.getConnection(url, "sa", "");
        // 使用项目的真实建表脚本，避免测试数据库与演示数据库的约束不同。
        try (InputStream schema = getClass().getResourceAsStream("/schema.sql");
             Statement statement = connection.createStatement()) {
            assertNotNull(schema, "项目必须包含 schema.sql");
            String sql = new String(schema.readAllBytes(), StandardCharsets.UTF_8);
            for (String command : sql.replaceAll("(?m)^\\s*--.*$", "").split(";")) {
                if (!command.trim().isEmpty()) {
                    statement.execute(command);
                }
            }
        }
    }

    @AfterEach
    void closeDatabase() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void studentCrudRoundTripPreservesDateBooleanAndDecimal() throws SQLException {
        Student student = student("S001", "张三");
        assertEquals(1, JDBCTool.save(student, connection));

        Student saved = JDBCTool.getOneById("S001", Student.class, connection);
        assertNotNull(saved);
        assertEquals("S001", saved.getId());
        assertEquals("张三", saved.getName());
        assertEquals("软件工程", saved.getMajor());
        assertEquals(Integer.valueOf(20), saved.getAge());
        assertEquals(LocalDate.of(2023, 9, 1), saved.getEnrollmentDate());
        assertEquals(Boolean.FALSE, saved.getGraduated());
        assertEquals(new BigDecimal("6800.50"), saved.getTuition());

        saved.setName("张三（已更新）");
        saved.setMajor("计算机科学与技术");
        saved.setAge(21);
        saved.setEnrollmentDate(LocalDate.of(2022, 9, 5));
        saved.setGraduated(true);
        saved.setTuition(new BigDecimal("7200.25"));
        assertEquals(1, JDBCTool.update(saved, connection));

        Student updated = JDBCTool.getOneById("S001", Student.class, connection);
        assertEquals("张三（已更新）", updated.getName());
        assertEquals("计算机科学与技术", updated.getMajor());
        assertEquals(Integer.valueOf(21), updated.getAge());
        assertEquals(LocalDate.of(2022, 9, 5), updated.getEnrollmentDate());
        assertEquals(Boolean.TRUE, updated.getGraduated());
        assertEquals(new BigDecimal("7200.25"), updated.getTuition());

        assertEquals(1, JDBCTool.delete(updated, connection));
        assertNull(JDBCTool.getOneById("S001", Student.class, connection));
        assertFalse(connection.isClosed());
    }

    @Test
    void collegeCrudRoundTrip() throws SQLException {
        College college = new College("C001", "信息科学与工程学院", "ISE");
        assertEquals(1, JDBCTool.save(college, connection));

        College saved = JDBCTool.getOneById("C001", College.class, connection);
        assertNotNull(saved);
        assertEquals("C001", saved.getId());
        assertEquals("信息科学与工程学院", saved.getName());
        assertEquals("ISE", saved.getCode());

        saved.setName("计算机学院");
        saved.setCode("CS");
        assertEquals(1, JDBCTool.update(saved, connection));
        College updated = JDBCTool.getOneById("C001", College.class, connection);
        assertEquals("计算机学院", updated.getName());
        assertEquals("CS", updated.getCode());

        assertEquals(1, JDBCTool.delete(updated, connection));
        assertNull(JDBCTool.getOneById("C001", College.class, connection));
        assertFalse(connection.isClosed());
    }

    @Test
    void resultSetMapsColumnLabelsCaseInsensitivelyAndIgnoresExtraColumns() throws SQLException {
        JDBCTool.save(student("S001", "张三"), connection);
        JDBCTool.save(student("S002", "李四"), connection);

        String sql = "SELECT id AS \"ID\", name AS \"NAME\", major AS \"MAJOR\", age AS \"AGE\","
                + " enrollment_date AS \"ENROLLMENT_DATE\", graduated AS \"GRADUATED\","
                + " tuition AS \"TUITION\", 99 AS unrelated_column FROM student ORDER BY id";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            List<Student> students = JDBCTool.resultSetToList(rs, Student.class);
            assertEquals(2, students.size());
            assertEquals("S001", students.get(0).getId());
            assertEquals("张三", students.get(0).getName());
            assertEquals("李四", students.get(1).getName());
            assertEquals(LocalDate.of(2023, 9, 1), students.get(1).getEnrollmentDate());
            assertEquals(Boolean.FALSE, students.get(1).getGraduated());
            assertEquals(new BigDecimal("6800.50"), students.get(1).getTuition());
            assertFalse(rs.isClosed());
        }
    }

    @Test
    void resultSetMapsCollegeAliasesByLabel() throws SQLException {
        JDBCTool.save(new College("C001", "信息学院", "ISE"), connection);
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT id AS \"Id\", name AS \"Name\", code AS \"Code\" FROM college")) {
            List<College> colleges = JDBCTool.resultSetToList(rs, College.class);
            assertEquals(1, colleges.size());
            assertEquals("C001", colleges.get(0).getId());
            assertEquals("信息学院", colleges.get(0).getName());
            assertEquals("ISE", colleges.get(0).getCode());
            assertFalse(rs.isClosed());
        }
    }

    @Test
    void emptyResultSetReturnsEmptyListAndLeavesResultSetOpen() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM student")) {
            List<Student> students = JDBCTool.resultSetToList(rs, Student.class);
            assertNotNull(students);
            assertTrue(students.isEmpty());
            assertFalse(rs.isClosed());
        }
        assertNull(JDBCTool.getOneById("missing", Student.class, connection));
        assertNull(JDBCTool.getOneById("missing", College.class, connection));
    }

    @Test
    void sqlNullsRemainNullInStudentAndCollege() throws SQLException {
        Student student = new Student("S001", "张三", null, null, null, null, null);
        College college = new College("C001", "信息学院", null);
        assertEquals(1, JDBCTool.save(student, connection));
        assertEquals(1, JDBCTool.save(college, connection));

        Student saved = JDBCTool.getOneById("S001", Student.class, connection);
        assertEquals("张三", saved.getName());
        assertNull(saved.getMajor());
        assertNull(saved.getAge());
        assertNull(saved.getEnrollmentDate());
        assertNull(saved.getGraduated());
        assertNull(saved.getTuition());
        College savedCollege = JDBCTool.getOneById("C001", College.class, connection);
        assertEquals("信息学院", savedCollege.getName());
        assertNull(savedCollege.getCode());
    }

    @Test
    void updatingOnePrimaryKeyLeavesOtherRowsUntouched() throws SQLException {
        JDBCTool.save(student("S001", "张三"), connection);
        JDBCTool.save(student("S002", "李四"), connection);
        JDBCTool.save(new College("C001", "信息学院", "ISE"), connection);
        JDBCTool.save(new College("C002", "土木学院", "CE"), connection);

        Student change = student("S001", "只修改张三");
        change.setAge(22);
        assertEquals(1, JDBCTool.update(change, connection));
        assertEquals("只修改张三", JDBCTool.getOneById("S001", Student.class, connection).getName());
        Student untouched = JDBCTool.getOneById("S002", Student.class, connection);
        assertEquals("李四", untouched.getName());
        assertEquals(Integer.valueOf(20), untouched.getAge());

        assertEquals(1, JDBCTool.update(new College("C001", "计算机学院", "CS"), connection));
        College untouchedCollege = JDBCTool.getOneById("C002", College.class, connection);
        assertEquals("土木学院", untouchedCollege.getName());
        assertEquals("CE", untouchedCollege.getCode());
        assertEquals(2, rowCount("student"));
        assertEquals(2, rowCount("college"));
    }

    @Test
    void deletingOnePrimaryKeyLeavesOtherRowsUntouched() throws SQLException {
        Student target = student("S001", "张三");
        JDBCTool.save(target, connection);
        JDBCTool.save(student("S002", "李四"), connection);
        College targetCollege = new College("C001", "信息学院", "ISE");
        JDBCTool.save(targetCollege, connection);
        JDBCTool.save(new College("C002", "土木学院", "CE"), connection);

        assertEquals(1, JDBCTool.delete(target, connection));
        assertNull(JDBCTool.getOneById("S001", Student.class, connection));
        assertEquals("李四", JDBCTool.getOneById("S002", Student.class, connection).getName());
        assertEquals(1, JDBCTool.delete(targetCollege, connection));
        assertNull(JDBCTool.getOneById("C001", College.class, connection));
        assertEquals("CE", JDBCTool.getOneById("C002", College.class, connection).getCode());
        assertEquals(1, rowCount("student"));
        assertEquals(1, rowCount("college"));
    }

    @Test
    void quoteAndSqlLookingValuesAreStoredAsData() throws SQLException {
        String text = "O'Brien'); DROP TABLE student; --";
        Student student = student("S001", text);
        student.setMajor("软件工程 ' OR '1'='1");
        assertEquals(1, JDBCTool.save(student, connection));
        Student saved = JDBCTool.getOneById("S001", Student.class, connection);
        assertEquals(text, saved.getName());
        assertEquals(student.getMajor(), saved.getMajor());

        College college = new College("C001", text, "x'); DELETE FROM college; --");
        assertEquals(1, JDBCTool.save(college, connection));
        college.setName("Updated O'Brien ' ; --");
        assertEquals(1, JDBCTool.update(college, connection));
        College savedCollege = JDBCTool.getOneById("C001", College.class, connection);
        assertEquals(college.getName(), savedCollege.getName());
        assertEquals(college.getCode(), savedCollege.getCode());
        assertEquals(1, rowCount("student"));
        assertEquals(1, rowCount("college"));
    }

    @Test
    void sqlLookingPrimaryKeyIsBoundForLookupUpdateAndDelete() throws SQLException {
        String trickyId = "' OR '1'='1";
        Student target = student(trickyId, "特殊学号");
        JDBCTool.save(target, connection);
        JDBCTool.save(student("S002", "李四"), connection);

        assertEquals("特殊学号", JDBCTool.getOneById(trickyId, Student.class, connection).getName());
        assertNull(JDBCTool.getOneById("' OR 1=1 --", Student.class, connection));
        target.setName("仅更新特殊学号");
        assertEquals(1, JDBCTool.update(target, connection));
        assertEquals("李四", JDBCTool.getOneById("S002", Student.class, connection).getName());
        assertEquals(1, JDBCTool.delete(target, connection));
        assertEquals(1, rowCount("student"));
        assertNotNull(JDBCTool.getOneById("S002", Student.class, connection));
    }

    @Test
    void updateCanReplaceExistingValuesWithSqlNull() throws SQLException {
        JDBCTool.save(student("S001", "张三"), connection);
        JDBCTool.save(new College("C001", "信息学院", "ISE"), connection);
        assertEquals(1, JDBCTool.update(
                new Student("S001", "张三", null, null, null, null, null), connection));
        assertEquals(1, JDBCTool.update(new College("C001", "信息学院", null), connection));

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT major, age, enrollment_date, graduated, tuition FROM student WHERE id = ?")) {
            statement.setString(1, "S001");
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                for (int column = 1; column <= 5; column++) {
                    assertNull(rs.getObject(column));
                }
            }
        }
        assertEquals("张三", JDBCTool.getOneById("S001", Student.class, connection).getName());
        College college = JDBCTool.getOneById("C001", College.class, connection);
        assertEquals("信息学院", college.getName());
        assertNull(college.getCode());
    }

    @Test
    void nullAndBlankPrimaryKeysAreRejectedForSaveUpdateDeleteAndLookup() throws SQLException {
        JDBCTool.save(student("S001", "张三"), connection);
        JDBCTool.save(new College("C001", "信息学院", "ISE"), connection);

        for (String invalidId : new String[]{null, "", "   "}) {
            Student invalidStudent = student(invalidId, "不能写入");
            College invalidCollege = new College(invalidId, "不能写入", "X");
            assertThrows(SQLException.class, () -> JDBCTool.save(invalidStudent, connection));
            assertThrows(SQLException.class, () -> JDBCTool.save(invalidCollege, connection));
            assertThrows(SQLException.class, () -> JDBCTool.update(invalidStudent, connection));
            assertThrows(SQLException.class, () -> JDBCTool.delete(invalidStudent, connection));
            assertThrows(SQLException.class, () -> JDBCTool.update(invalidCollege, connection));
            assertThrows(SQLException.class, () -> JDBCTool.delete(invalidCollege, connection));
            assertThrows(SQLException.class,
                    () -> JDBCTool.getOneById(invalidId, Student.class, connection));
            assertThrows(SQLException.class,
                    () -> JDBCTool.getOneById(invalidId, College.class, connection));
        }
        assertEquals("张三", JDBCTool.getOneById("S001", Student.class, connection).getName());
        assertEquals("ISE", JDBCTool.getOneById("C001", College.class, connection).getCode());
        assertEquals(1, rowCount("student"));
        assertEquals(1, rowCount("college"));
        assertFalse(connection.isClosed());
    }

    @Test
    void missingPrimaryKeyMetadataIsRejected() throws SQLException {
        MissingPrimaryKey invalid = new MissingPrimaryKey();
        assertThrows(SQLException.class, () -> JDBCTool.update(invalid, connection));
        assertThrows(SQLException.class, () -> JDBCTool.delete(invalid, connection));
        assertThrows(SQLException.class,
                () -> JDBCTool.getOneById("C001", MissingPrimaryKey.class, connection));
        assertFalse(connection.isClosed());
    }

    @Test
    void databaseConstraintErrorsPropagateWithoutClosingCallerConnection() throws SQLException {
        JDBCTool.save(student("S001", "原始学生"), connection);
        JDBCTool.save(new College("C001", "原始学院", "OLD"), connection);

        assertThrows(SQLException.class,
                () -> JDBCTool.save(student("S001", "重复学生"), connection));
        assertThrows(SQLException.class,
                () -> JDBCTool.save(new College("C001", "重复学院", "NEW"), connection));
        assertThrows(SQLException.class,
                () -> JDBCTool.save(student("S002", null), connection));
        assertThrows(SQLException.class,
                () -> JDBCTool.save(new College("C002", null, "NONE"), connection));

        assertFalse(connection.isClosed());
        assertEquals("原始学生", JDBCTool.getOneById("S001", Student.class, connection).getName());
        assertEquals("OLD", JDBCTool.getOneById("C001", College.class, connection).getCode());
        assertEquals(1, rowCount("student"));
        assertEquals(1, rowCount("college"));
    }

    @Test
    void updateAndDeleteOfMissingRowsReturnZero() throws SQLException {
        Student student = student("missing-student", "不存在");
        College college = new College("missing-college", "不存在", "NONE");
        assertEquals(0, JDBCTool.update(student, connection));
        assertEquals(0, JDBCTool.delete(student, connection));
        assertEquals(0, JDBCTool.update(college, connection));
        assertEquals(0, JDBCTool.delete(college, connection));
        assertEquals(0, rowCount("student"));
        assertEquals(0, rowCount("college"));
        assertFalse(connection.isClosed());
    }

    @Test
    void callerCanRollBackSavesInItsOwnTransaction() throws SQLException {
        connection.setAutoCommit(false);
        assertEquals(1, JDBCTool.save(student("S001", "待回滚学生"), connection));
        assertEquals(1, JDBCTool.save(new College("C001", "待回滚学院", "TEMP"), connection));
        assertFalse(connection.getAutoCommit());
        assertEquals(1, rowCount("student"));
        assertEquals(1, rowCount("college"));

        connection.rollback();

        assertEquals(0, rowCount("student"));
        assertEquals(0, rowCount("college"));
        assertNull(JDBCTool.getOneById("S001", Student.class, connection));
        assertNull(JDBCTool.getOneById("C001", College.class, connection));
        assertFalse(connection.isClosed());
        assertFalse(connection.getAutoCommit());
    }

    @Test
    void sqlNullCannotSilentlyBecomePrimitiveZero() throws SQLException {
        JDBCTool.save(new Student("S001", "年龄未知", null, null, null, null, null), connection);
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT age FROM student WHERE id = 'S001'")) {
            assertThrows(SQLException.class,
                    () -> JDBCTool.resultSetToList(rs, PrimitiveAge.class));
            assertFalse(rs.isClosed());
        }
        assertFalse(connection.isClosed());
    }

    private Student student(String id, String name) {
        return new Student(id, name, "软件工程", 20, LocalDate.of(2023, 9, 1),
                false, new BigDecimal("6800.50"));
    }

    private int rowCount(String table) throws SQLException {
        // table 只接受测试代码中的固定字符串，不接受用户输入。
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    @Table(name = "college")
    public static class MissingPrimaryKey {
        private String name = "缺少主键注解";
        private String code = "INVALID";

        public MissingPrimaryKey() {
        }
    }

    @Table(name = "student")
    public static class PrimitiveAge {
        private int age;

        public PrimitiveAge() {
        }
    }
}
