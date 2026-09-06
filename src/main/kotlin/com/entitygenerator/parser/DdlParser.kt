package com.entitygenerator.parser

import com.entitygenerator.model.Column
import com.entitygenerator.model.Table
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.create.table.ColumnDefinition
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.create.table.Index

class DdlParser {

    fun parse(ddl: String): List<Table> =
        splitStatements(ddl).mapNotNull { statement ->
            runCatching { CCJSqlParserUtil.parse(statement) }
                .getOrNull()
                ?.let { it as? CreateTable }
                ?.let(::toTable)
        }

    private fun splitStatements(ddl: String): List<String> =
        ddl.split(";").map { it.trim() }.filter { it.isNotEmpty() }

    private fun toTable(createTable: CreateTable): Table {
        // Table-level constraint: PRIMARY KEY (col1, col2)
        val tableLevelPk = createTable.indexes
            ?.filterIsInstance<Index>()
            ?.firstOrNull { it.type == "PRIMARY KEY" }
            ?.columnsNames
            ?.toSet()
            ?: emptySet()

        // Inline column-level: col_name TYPE PRIMARY KEY
        val inlinePk = createTable.columnDefinitions
            .filter { colDef ->
                val specs = colDef.columnSpecs?.map { it.uppercase() } ?: emptyList()
                specs.zipWithNext().any { it == "PRIMARY" to "KEY" }
            }
            .map { it.columnName }
            .toSet()

        val primaryKeyColumns = tableLevelPk + inlinePk
        val columns = createTable.columnDefinitions.map { toColumn(it, primaryKeyColumns) }
        return Table(name = createTable.table.name, columns = columns)
    }

    private fun toColumn(colDef: ColumnDefinition, primaryKeyColumns: Set<String>): Column {
        val sqlType = colDef.colDataType.dataType.uppercase()
        val length = colDef.colDataType.argumentsStringList?.firstOrNull()?.toIntOrNull()
        val specs = colDef.columnSpecs?.map { it.uppercase() } ?: emptyList()
        val notNullDeclared = specs.zipWithNext().any { it == "NOT" to "NULL" }
        val isPk = primaryKeyColumns.contains(colDef.columnName)

        return Column(
            name = colDef.columnName,
            sqlType = sqlType,
            javaType = mapSqlTypeToJavaType(sqlType),
            nullable = !notNullDeclared && !isPk,
            isPrimaryKey = isPk,
            length = length
        )
    }

    private fun mapSqlTypeToJavaType(sqlType: String): String = when {
        sqlType.startsWith("VARCHAR") || sqlType.startsWith("CHAR") || sqlType == "TEXT" -> "String"
        sqlType.startsWith("BIGINT") || sqlType == "BIGSERIAL" -> "Long"
        sqlType.startsWith("INT") || sqlType == "SERIAL" -> "Integer"
        sqlType.startsWith("BOOL") -> "Boolean"
        sqlType.startsWith("DECIMAL") || sqlType.startsWith("NUMERIC") -> "BigDecimal"
        sqlType == "DATE" -> "LocalDate"
        sqlType.startsWith("TIMESTAMP") -> "LocalDateTime"
        else -> "String"
    }
}