package edu.homework.jdbc.util;

import edu.homework.jdbc.annotation.Column;
import edu.homework.jdbc.annotation.Id;
import edu.homework.jdbc.annotation.Table;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 使用注解、反射和 JDBC 实现简单的对象关系映射。
 * 调用者创建并关闭 Connection；本工具只关闭自己创建的语句和结果集。
 */
public final class JDBCTool {
    private JDBCTool() {
    }

    /** 方法1：把结果集的每一行转换成一个对象，列标签也支持 SQL 中的别名。 */
    public static <T> List<T> resultSetToList(ResultSet rs, Class<T> clazz) throws SQLException {
        if (rs == null) {
            throw new SQLException("ResultSet 不能为 null");
        }
        EntityMapping mapping = inspect(clazz);
        Constructor<T> constructor;
        try {
            constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new SQLException("实体类必须提供可访问的无参构造方法：" + clazz.getName(), e);
        }

        Map<String, Field> fieldsByName = new HashMap<>();
        for (Field field : mapping.fields) {
            addMapping(fieldsByName, field.getName(), field);
            addMapping(fieldsByName, columnName(field), field);
        }
        ResultSetMetaData metadata = rs.getMetaData();
        Field[] columnFields = new Field[metadata.getColumnCount()];
        for (int i = 0; i < columnFields.length; i++) {
            columnFields[i] = fieldsByName.get(metadata.getColumnLabel(i + 1).toLowerCase(Locale.ROOT));
        }

        List<T> objects = new ArrayList<>();
        while (rs.next()) {
            T object;
            try {
                object = constructor.newInstance();
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new SQLException("反射创建对象失败：" + clazz.getName(), e);
            }
            for (int i = 0; i < columnFields.length; i++) {
                Field field = columnFields[i];
                if (field == null) {
                    continue; // 查询中多出的列不参与实体映射。
                }
                Object value = rs.getObject(i + 1);
                if (value == null && field.getType().isPrimitive()) {
                    // 使用包装类型才能准确保留 SQL NULL，避免误映射成 0 或 false。
                    throw new SQLException("SQL NULL 不能映射到基本类型属性：" + field.getName()
                            + "，请使用对应的包装类型");
                }
                try {
                    field.set(object, convert(value, field.getType()));
                } catch (IllegalAccessException | RuntimeException e) {
                    throw new SQLException("属性赋值失败：" + clazz.getName() + "." + field.getName(), e);
                }
            }
            objects.add(object);
        }
        return objects;
    }

    /** 方法2：生成 INSERT 语句并保存对象，返回受影响的行数。 */
    public static <T> int save(T obj, Connection connection) throws SQLException {
        checkArguments(obj, connection);
        EntityMapping mapping = inspect(obj.getClass());
        if (mapping.id != null) {
            requireIdValue(read(mapping.id, obj)); // 本作业使用手工指定主键，不处理自增主键。
        }
        StringJoiner columns = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");
        for (Field field : mapping.fields) {
            columns.add(columnName(field));
            placeholders.add("?");
        }
        String sql = "INSERT INTO " + mapping.table + " (" + columns + ") VALUES (" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < mapping.fields.size(); i++) {
                bind(statement, i + 1, read(mapping.fields.get(i), obj));
            }
            return statement.executeUpdate();
        }
    }

    /** 方法3：以 @Id 属性为条件更新其余字段；主键不参与 SET。 */
    public static <T> int update(T obj, Connection connection) throws SQLException {
        checkArguments(obj, connection);
        EntityMapping mapping = inspect(obj.getClass());
        Field idField = requireId(mapping);
        Object id = requireIdValue(read(idField, obj));
        List<Field> updateFields = new ArrayList<>();
        StringJoiner assignments = new StringJoiner(", ");
        for (Field field : mapping.fields) {
            if (!field.equals(idField)) {
                updateFields.add(field);
                assignments.add(columnName(field) + " = ?");
            }
        }
        if (updateFields.isEmpty()) {
            throw new SQLException("实体没有可更新的非主键属性");
        }
        String sql = "UPDATE " + mapping.table + " SET " + assignments + " WHERE " + columnName(idField) + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < updateFields.size(); i++) {
                bind(statement, i + 1, read(updateFields.get(i), obj));
            }
            bind(statement, updateFields.size() + 1, id);
            return statement.executeUpdate();
        }
    }

    /** 方法4：仅删除对象主键对应的一行，拒绝空主键。 */
    public static <T> int delete(T obj, Connection connection) throws SQLException {
        checkArguments(obj, connection);
        EntityMapping mapping = inspect(obj.getClass());
        Field idField = requireId(mapping);
        Object id = requireIdValue(read(idField, obj));
        String sql = "DELETE FROM " + mapping.table + " WHERE " + columnName(idField) + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, 1, id);
            return statement.executeUpdate();
        }
    }

    /** 方法5：按主键查询一个对象；找不到记录时返回 null。 */
    public static <T> T getOneById(String id, Class<T> clazz, Connection connection) throws SQLException {
        if (connection == null) {
            throw new SQLException("Connection 不能为 null");
        }
        requireIdValue(id);
        EntityMapping mapping = inspect(clazz);
        Field idField = requireId(mapping);
        String sql = "SELECT * FROM " + mapping.table + " WHERE " + columnName(idField) + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, 1, convert(id, idField.getType()));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> objects = resultSetToList(resultSet, clazz);
                return objects.isEmpty() ? null : objects.get(0);
            }
        } catch (RuntimeException e) {
            throw new SQLException("主键类型转换失败：" + idField.getName(), e);
        }
    }

    // 表名和列名不能使用占位符，因此只允许字母、数字和下划线组成的标识符。
    private static String identifier(String name) throws SQLException {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new SQLException("不合法的数据库表名或列名：" + name);
        }
        return name;
    }

    private static String columnName(Field field) throws SQLException {
        Column column = field.getAnnotation(Column.class);
        return identifier(column == null ? field.getName() : column.name());
    }

    // 提取映射信息；继承的属性也可参与映射，静态和 transient 属性不入库。
    private static EntityMapping inspect(Class<?> clazz) throws SQLException {
        if (clazz == null) {
            throw new SQLException("实体 Class 不能为 null");
        }
        Table table = clazz.getAnnotation(Table.class);
        EntityMapping mapping = new EntityMapping();
        mapping.table = identifier(table == null ? clazz.getSimpleName().toLowerCase(Locale.ROOT) : table.name());
        Map<String, Field> columns = new HashMap<>();
        for (Class<?> current = clazz; current != Object.class && current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                } catch (RuntimeException e) {
                    throw new SQLException("无法访问实体属性：" + field.getName(), e);
                }
                addMapping(columns, columnName(field), field);
                mapping.fields.add(field);
                if (field.isAnnotationPresent(Id.class)) {
                    if (mapping.id != null) {
                        throw new SQLException("实体只能标记一个 @Id 属性：" + clazz.getName());
                    }
                    mapping.id = field;
                }
            }
        }
        if (mapping.fields.isEmpty()) {
            throw new SQLException("实体没有可映射的属性：" + clazz.getName());
        }
        return mapping;
    }

    private static void addMapping(Map<String, Field> mapping, String name, Field field) throws SQLException {
        Field previous = mapping.putIfAbsent(name.toLowerCase(Locale.ROOT), field);
        if (previous != null && !previous.equals(field)) {
            throw new SQLException("多个属性使用了相同的映射名称：" + name);
        }
    }

    private static Field requireId(EntityMapping mapping) throws SQLException {
        if (mapping.id == null) {
            throw new SQLException("按主键操作要求实体具有一个 @Id 属性");
        }
        return mapping.id;
    }

    private static Object requireIdValue(Object id) throws SQLException {
        if (id == null || (id instanceof String && ((String) id).trim().isEmpty())) {
            throw new SQLException("主键不能为 null 或空字符串");
        }
        return id;
    }

    private static void checkArguments(Object obj, Connection connection) throws SQLException {
        if (obj == null || connection == null) {
            throw new SQLException("实体对象和 Connection 均不能为 null");
        }
    }

    private static Object read(Field field, Object obj) throws SQLException {
        try {
            return field.get(obj);
        } catch (IllegalAccessException | RuntimeException e) {
            throw new SQLException("读取属性失败：" + field.getName(), e);
        }
    }

    // 日期明确转换为 JDBC 日期类型，其余值交给驱动处理；参数始终不拼接进 SQL。
    private static void bind(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof LocalDate) {
            statement.setDate(index, java.sql.Date.valueOf((LocalDate) value));
        } else if (value instanceof LocalDateTime) {
            statement.setTimestamp(index, Timestamp.valueOf((LocalDateTime) value));
        } else {
            statement.setObject(index, value);
        }
    }

    // 数据库驱动返回的类型可能不同于属性类型，赋值前进行常见类型转换。
    private static Object convert(Object value, Class<?> type) throws SQLException {
        if (value == null || type.isInstance(value)) {
            return value;
        }
        try {
            if (type == String.class) return value.toString();
            if (type == Integer.class || type == int.class) return number(value).intValue();
            if (type == Long.class || type == long.class) return number(value).longValue();
            if (type == Short.class || type == short.class) return number(value).shortValue();
            if (type == Byte.class || type == byte.class) return number(value).byteValue();
            if (type == Double.class || type == double.class) return number(value).doubleValue();
            if (type == Float.class || type == float.class) return number(value).floatValue();
            if (type == BigDecimal.class) return new BigDecimal(value.toString());
            if (type == Boolean.class || type == boolean.class) {
                if (value instanceof Boolean) return value;
                if (value instanceof Number) return number(value).doubleValue() != 0;
                String text = value.toString().trim();
                if (text.equalsIgnoreCase("true") || text.equals("1")) return true;
                if (text.equalsIgnoreCase("false") || text.equals("0")) return false;
            }
            if (type == LocalDate.class) {
                if (value instanceof java.sql.Date) return ((java.sql.Date) value).toLocalDate();
                if (value instanceof Timestamp) return ((Timestamp) value).toLocalDateTime().toLocalDate();
                if (value instanceof LocalDateTime) return ((LocalDateTime) value).toLocalDate();
                return LocalDate.parse(value.toString());
            }
            if (type == LocalDateTime.class) {
                if (value instanceof Timestamp) return ((Timestamp) value).toLocalDateTime();
                if (value instanceof java.sql.Date) return ((java.sql.Date) value).toLocalDate().atStartOfDay();
                if (value instanceof LocalDate) return ((LocalDate) value).atStartOfDay();
                return LocalDateTime.parse(value.toString().replace(' ', 'T'));
            }
            if (type == java.sql.Date.class) {
                if (value instanceof LocalDate) return java.sql.Date.valueOf((LocalDate) value);
                if (value instanceof java.util.Date) return new java.sql.Date(((java.util.Date) value).getTime());
                return java.sql.Date.valueOf(value.toString());
            }
            if (type == Timestamp.class) {
                if (value instanceof LocalDateTime) return Timestamp.valueOf((LocalDateTime) value);
                if (value instanceof java.util.Date) return new Timestamp(((java.util.Date) value).getTime());
                return Timestamp.valueOf(value.toString().replace('T', ' '));
            }
        } catch (RuntimeException e) {
            throw new SQLException("无法将值转换为 " + type.getName(), e);
        }
        throw new SQLException("不支持的属性类型转换：" + value.getClass().getName() + " -> " + type.getName());
    }

    private static Number number(Object value) {
        return value instanceof Number ? (Number) value : new BigDecimal(value.toString());
    }

    private static final class EntityMapping {
        private String table;
        private final List<Field> fields = new ArrayList<>();
        private Field id;
    }
}
