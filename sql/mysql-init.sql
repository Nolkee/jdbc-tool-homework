-- 首次使用 MySQL 时可执行本文件，已有表和数据会保留。
CREATE DATABASE IF NOT EXISTS DateTest
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE DateTest;

CREATE TABLE IF NOT EXISTS student (
    id VARCHAR(40) PRIMARY KEY COMMENT '学生编号',
    name VARCHAR(80) NOT NULL COMMENT '学生姓名',
    major VARCHAR(80) COMMENT '专业',
    age INT COMMENT '年龄',
    enrollment_date DATE COMMENT '入学日期',
    graduated BOOLEAN COMMENT '是否毕业',
    tuition DECIMAL(10, 2) COMMENT '学费'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生表';

CREATE TABLE IF NOT EXISTS college (
    id VARCHAR(40) PRIMARY KEY COMMENT '学院编号',
    name VARCHAR(80) NOT NULL COMMENT '学院名称',
    code VARCHAR(30) COMMENT '学院代码'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学院表';
