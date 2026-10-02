package edu.homework.jdbc.entity;

import edu.homework.jdbc.annotation.Id;
import edu.homework.jdbc.annotation.Table;

/** 学院表的实体类。 */
@Table(name = "college")
public class College {
    @Id
    private String id;
    private String name;
    private String code;

    public College() {
    }

    public College(String id, String name, String code) {
        this.id = id;
        this.name = name;
        this.code = code;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    @Override
    public String toString() {
        return "College{id='" + id + "', name='" + name + "', code='" + code + "'}";
    }
}
