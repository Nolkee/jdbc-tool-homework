package edu.homework.jdbc.entity;

import edu.homework.jdbc.annotation.Column;
import edu.homework.jdbc.annotation.Id;
import edu.homework.jdbc.annotation.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 学生表的实体类；属性与数据表字段对应。 */
@Table(name = "student")
public class Student {
    @Id
    private String id;
    private String name;
    private String major;
    private Integer age;
    @Column(name = "enrollment_date")
    private LocalDate enrollmentDate;
    private Boolean graduated;
    private BigDecimal tuition;

    // 反射创建对象时使用无参构造方法。
    public Student() {
    }

    public Student(String id, String name, String major, Integer age,
                   LocalDate enrollmentDate, Boolean graduated, BigDecimal tuition) {
        this.id = id;
        this.name = name;
        this.major = major;
        this.age = age;
        this.enrollmentDate = enrollmentDate;
        this.graduated = graduated;
        this.tuition = tuition;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public LocalDate getEnrollmentDate() { return enrollmentDate; }
    public void setEnrollmentDate(LocalDate enrollmentDate) { this.enrollmentDate = enrollmentDate; }
    public Boolean getGraduated() { return graduated; }
    public void setGraduated(Boolean graduated) { this.graduated = graduated; }
    public BigDecimal getTuition() { return tuition; }
    public void setTuition(BigDecimal tuition) { this.tuition = tuition; }

    @Override
    public String toString() {
        return "Student{id='" + id + "', name='" + name + "', major='" + major
                + "', age=" + age + ", enrollmentDate=" + enrollmentDate
                + ", graduated=" + graduated + ", tuition=" + tuition + "}";
    }
}
