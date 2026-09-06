package com.entitygenerator.db

import com.entitygenerator.model.Column
import com.entitygenerator.model.Table
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.Types

class JdbcSchemaReader(private val connection: Connection) {

    fun listTables(): List<String> {
        val tables = mutableListOf<String>()
        connection.metaData.getTables(connection.catalog, connection.schema, "%", arrayOf("TABLE")).use { rs ->
            while (rs.next()) tables += rs.getString("TABLE_NAME")
        }
        return tables
    }

    fun readTable(tableName: String): Table {
        val primaryKeyColumns = mutableSetOf<String>()
        connection.metaData.getPrimaryKeys(connection.catalog, connection.schema, tableName).use { rs ->
            while (rs.next()) primaryKeyColumns += rs.getString("COLUMN_NAME")
        }

        val columns = mutableListOf<Column>()
        connection.metaData.getColumns(connection.catalog, connection.schema, tableName, "%").use { rs ->
            while (rs.next()) {
                val name = rs.getString("COLUMN_NAME")
                val sqlTypeCode = rs.getInt("DATA_TYPE")
                val typeName = rs.getString("TYPE_NAME")
                val nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable
                val length = rs.getInt("COLUMN_SIZE").takeIf { it > 0 }
                val isPk = primaryKeyColumns.contains(name)

                columns += Column(
                    name = name,
                    sqlType = typeName,
                    javaType = mapJdbcTypeToJavaType(sqlTypeCode),
                    nullable = nullable && !isPk,
                    isPrimaryKey = isPk,
                    length = length
                )
            }
        }

        return Table(name = tableName, columns = columns)
    }

    private fun mapJdbcTypeToJavaType(sqlType: Int): String = when (sqlType) {
        Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.CLOB -> "String"
        Types.BIGINT -> "Long"
        Types.INTEGER, Types.SMALLINT, Types.TINYINT -> "Integer"
        Types.BOOLEAN, Types.BIT -> "Boolean"
        Types.DECIMAL, Types.NUMERIC -> "BigDecimal"
        Types.DATE -> "LocalDate"
        Types.TIMESTAMP -> "LocalDateTime"
        else -> "String"
    }
}