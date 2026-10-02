package edu.homework.jdbc;

import edu.homework.jdbc.entity.College;
import edu.homework.jdbc.entity.Student;
import edu.homework.jdbc.util.DatabaseConfig;
import edu.homework.jdbc.util.DatabaseInitializer;
import edu.homework.jdbc.util.JDBCTool;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 作业演示入口：每次使用独立的演示编号，只修改本次插入的数据。 */
public final class Demo {
    private Demo() { }

    public static void main(String[] args) throws SQLException {
        String mode = "h2";
        String scope = "all";
        for (String arg : args) {
            switch (arg) {
                case "--h2": mode = "h2"; break;
                case "--mysql": mode = "mysql"; break;
                case "--student": scope = "student"; break;
                case "--college": scope = "college"; break;
                default: throw new IllegalArgumentException("参数应为 --h2、--mysql、--student 或 --college");
            }
        }
        String runId = UUID.randomUUID().toString().substring(0, 8);
        System.out.println("JDBC工具的设计与开发 | Maven + 反射 + JDBC");
        try (Connection connection = DatabaseConfig.open(mode)) {
            DatabaseInitializer.initialize(connection);
            System.out.println("数据库: " + connection.getMetaData().getDatabaseProductName()
                    + " | DateTest | student、college 两张表已就绪");
            System.out.println("本次演示编号: " + runId + "（记录保存在本地数据库）");
            // 同一连接上的多个操作构成事务，由调用者负责提交或回滚。
            connection.setAutoCommit(false);
            try {
                if (!"college".equals(scope)) {
                    demoStudent(connection, runId);
                }
                if (!"student".equals(scope)) {
                    demoCollege(connection, runId);
                }
                connection.commit();
                System.out.println("\n[PASS] 事务已提交，所选表的增删改查验证全部通过。");
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }

    private static void demoStudent(Connection connection, String runId) throws SQLException {
        System.out.println("\n========== 学生表 Student ==========");
        Student first = new Student("S" + runId + "01", "张三", "软件工程", 21,
                LocalDate.of(2024, 9, 1), false, new BigDecimal("5800.00"));
        Student second = new Student("S" + runId + "02", "李四", "计算机科学", 22,
                LocalDate.of(2023, 9, 1), false, new BigDecimal("6000.00"));
        System.out.println("1. save：插入两名学生，影响行数 = " + JDBCTool.save(first, connection)
                + "、" + JDBCTool.save(second, connection));
        List<Student> students = studentList(connection, first.getId(), second.getId());
        check(students.size() == 2, "学生列表应有两条记录");
        System.out.println("2. resultSetToList：查询到 " + students.size() + " 个学生对象");
        students.forEach(Demo::printStudent);

        Student found = JDBCTool.getOneById(first.getId(), Student.class, connection);
        check(found != null && "张三".equals(found.getName()), "按主键查询学生");
        check(first.getEnrollmentDate().equals(found.getEnrollmentDate()), "日期映射");
        check(Boolean.FALSE.equals(found.getGraduated()), "布尔映射");
        check(first.getTuition().compareTo(found.getTuition()) == 0, "学费精度");
        System.out.println("3. getOneById：按主键获取张三成功（日期、布尔、金额映射正确）");

        found.setAge(23);
        found.setMajor("人工智能");
        found.setGraduated(true);
        found.setTuition(new BigDecimal("6200.50"));
        check(JDBCTool.update(found, connection) == 1, "更新应影响一行");
        Student updated = JDBCTool.getOneById(first.getId(), Student.class, connection);
        check(updated != null && updated.getAge() == 23 && "人工智能".equals(updated.getMajor())
                && Boolean.TRUE.equals(updated.getGraduated())
                && new BigDecimal("6200.50").compareTo(updated.getTuition()) == 0, "更新值已写入数据库");
        Student untouched = JDBCTool.getOneById(second.getId(), Student.class, connection);
        check(untouched != null && untouched.getAge() == 22, "其他学生不受更新影响");
        System.out.println("4. update：影响行数 = 1，再次查询得到：");
        printStudent(updated);

        check(JDBCTool.delete(second, connection) == 1, "删除应影响一行");
        check(JDBCTool.getOneById(second.getId(), Student.class, connection) == null, "删除后应查不到对象");
        System.out.println("5. delete：删除李四，影响行数 = 1；再次查询 = null");
        check(studentList(connection, first.getId(), second.getId()).size() == 1, "本次演示应保留一个学生");
        System.out.println("[PASS] 学生表五个方法验证通过，本次演示保留 1 条记录。");
    }

    private static void demoCollege(Connection connection, String runId) throws SQLException {
        System.out.println("\n========== 学院表 College ==========");
        College first = new College("C" + runId + "01", "信息科学与工程学院", "CS");
        College second = new College("C" + runId + "02", "经济管理学院", "EM");
        System.out.println("1. save：插入两个学院，影响行数 = " + JDBCTool.save(first, connection)
                + "、" + JDBCTool.save(second, connection));
        List<College> colleges = collegeList(connection, first.getId(), second.getId());
        check(colleges.size() == 2, "学院列表应有两条记录");
        System.out.println("2. resultSetToList：查询到 " + colleges.size() + " 个学院对象");
        colleges.forEach(Demo::printCollege);

        College found = JDBCTool.getOneById(first.getId(), College.class, connection);
        check(found != null && "CS".equals(found.getCode()), "按主键查询学院");
        System.out.println("3. getOneById：按主键获取信息科学与工程学院成功");
        found.setName("计算机与人工智能学院");
        found.setCode("AI");
        check(JDBCTool.update(found, connection) == 1, "更新应影响一行");
        College updated = JDBCTool.getOneById(first.getId(), College.class, connection);
        check(updated != null && "AI".equals(updated.getCode())
                && "计算机与人工智能学院".equals(updated.getName()), "更新值已写入数据库");
        College untouched = JDBCTool.getOneById(second.getId(), College.class, connection);
        check(untouched != null && "EM".equals(untouched.getCode()), "其他学院不受更新影响");
        System.out.println("4. update：影响行数 = 1，再次查询得到：");
        printCollege(updated);
        check(JDBCTool.delete(second, connection) == 1, "删除应影响一行");
        check(JDBCTool.getOneById(second.getId(), College.class, connection) == null, "删除后应查不到对象");
        System.out.println("5. delete：删除经济管理学院，影响行数 = 1；再次查询 = null");
        check(collegeList(connection, first.getId(), second.getId()).size() == 1, "本次演示应保留一个学院");
        System.out.println("[PASS] 学院表五个方法验证通过，本次演示保留 1 条记录。");
    }

    private static List<Student> studentList(Connection connection, String firstId, String secondId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM student WHERE id IN (?, ?) ORDER BY id")) {
            statement.setString(1, firstId);
            statement.setString(2, secondId);
            try (ResultSet rs = statement.executeQuery()) {
                return JDBCTool.resultSetToList(rs, Student.class);
            }
        }
    }

    private static List<College> collegeList(Connection connection, String firstId, String secondId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM college WHERE id IN (?, ?) ORDER BY id")) {
            statement.setString(1, firstId);
            statement.setString(2, secondId);
            try (ResultSet rs = statement.executeQuery()) {
                return JDBCTool.resultSetToList(rs, College.class);
            }
        }
    }

    private static void printStudent(Student s) {
        System.out.println("   id=" + s.getId() + " 姓名=" + s.getName() + " 专业=" + s.getMajor() + " 年龄=" + s.getAge());
        System.out.println("   入学=" + s.getEnrollmentDate() + " 是否毕业=" + s.getGraduated() + " 学费=" + s.getTuition());
    }

    private static void printCollege(College c) {
        System.out.println("   id=" + c.getId() + " 学院=" + c.getName() + " code=" + c.getCode());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("验证失败：" + message);
        }
    }
}
