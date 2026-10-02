-- 学生表：编号、姓名、专业、年龄、入学日期、是否毕业、学费。
CREATE TABLE IF NOT EXISTS student (
    id VARCHAR(40) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    major VARCHAR(80),
    age INT,
    enrollment_date DATE,
    graduated BOOLEAN,
    tuition DECIMAL(10, 2)
);

-- 学院表：编号、名称、学院代码。
CREATE TABLE IF NOT EXISTS college (
    id VARCHAR(40) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    code VARCHAR(30)
);
