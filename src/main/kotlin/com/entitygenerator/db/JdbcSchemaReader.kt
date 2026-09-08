package com.entitygenerator.db

import com.entitygenerator.model.Column
import com.entitygenerator.model.ForeignKey
import com.entitygenerator.model.Table
import com.entitygenerator.model.UniqueConstraint
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.Types

data class TableRef(val schema: String, val name: String)

class JdbcSchemaReader(private val connection: Connection) {

    fun listTables(): List<TableRef> {
        val tables = mutableListOf<TableRef>()
        connection.metaData.getTables(connection.catalog, "%", "%", arrayOf("TABLE")).use { rs ->
            while (rs.next()) {
                val schema = rs.getString("TABLE_SCHEM") ?: ""
                val name = rs.getString("TABLE_NAME")
                tables += TableRef(schema, name)
            }
        }
        return tables
    }

    fun readTable(ref: TableRef): Table {
        val primaryKeyColumns = mutableSetOf<String>()
        connection.metaData.getPrimaryKeys(connection.catalog, ref.schema, ref.name).use { rs ->
            while (rs.next()) primaryKeyColumns += rs.getString("COLUMN_NAME")
        }

        val columns = mutableListOf<Column>()
        connection.metaData.getColumns(connection.catalog, ref.schema, ref.name, "%").use { rs ->
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

        return Table(
            name = ref.name,
            schema = ref.schema.ifBlank { null },
            columns = columns,
            foreignKeys = readForeignKeys(ref),
            uniqueConstraints = readUniqueConstraints(ref, primaryKeyColumns)
        )
    }

    private fun readForeignKeys(ref: TableRef): List<ForeignKey> {
        data class Row(val fkName: String, val fkColumn: String, val pkColumn: String, val pkTable: String, val seq: Int)
        val rows = mutableListOf<Row>()

        connection.metaData.getImportedKeys(connection.catalog, ref.schema, ref.name).use { rs ->
            while (rs.next()) {
                rows += Row(
                    fkName = rs.getString("FK_NAME") ?: "${ref.name}_${rs.getString("FKCOLUMN_NAME")}_fk",
                    fkColumn = rs.getString("FKCOLUMN_NAME"),
                    pkColumn = rs.getString("PKCOLUMN_NAME"),
                    pkTable = rs.getString("PKTABLE_NAME"),
                    seq = rs.getInt("KEY_SEQ")
                )
            }
        }

        return rows.groupBy { it.fkName }.map { (fkName, group) ->
            val ordered = group.sortedBy { it.seq }
            ForeignKey(
                name = fkName,
                columns = ordered.map { it.fkColumn },
                referencedTable = ordered.first().pkTable,
                referencedColumns = ordered.map { it.pkColumn }
            )
        }
    }

    private fun readUniqueConstraints(ref: TableRef, primaryKeyColumns: Set<String>): List<UniqueConstraint> {
        data class Row(val indexName: String, val column: String, val ordinal: Int)
        val rows = mutableListOf<Row>()

        connection.metaData.getIndexInfo(connection.catalog, ref.schema, ref.name, true, false).use { rs ->
            while (rs.next()) {
                val indexName = rs.getString("INDEX_NAME") ?: continue
                val column = rs.getString("COLUMN_NAME") ?: continue
                rows += Row(indexName, column, rs.getInt("ORDINAL_POSITION"))
            }
        }

        return rows.groupBy { it.indexName }
            .map { (name, group) -> name to group.sortedBy { it.ordinal }.map { it.column } }
            .filter { (_, columns) -> columns.toSet() != primaryKeyColumns }
            .map { (name, columns) -> UniqueConstraint(name = name, columns = columns) }
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