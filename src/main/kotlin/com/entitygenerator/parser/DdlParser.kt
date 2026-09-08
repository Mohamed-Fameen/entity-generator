package com.entitygenerator.parser

import com.entitygenerator.model.Column
import com.entitygenerator.model.ForeignKey
import com.entitygenerator.model.Table
import com.entitygenerator.model.UniqueConstraint
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.create.table.ColumnDefinition
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex
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
        val allIndexes = createTable.indexes.orEmpty()

        val tableLevelPk = allIndexes
            .filterIsInstance<Index>()
            .firstOrNull { it.type == "PRIMARY KEY" }
            ?.columnsNames
            ?.toSet()
            ?: emptySet()

        val inlinePk = createTable.columnDefinitions
            .filter { hasSpecPair(it, "PRIMARY", "KEY") }
            .map { it.columnName }
            .toSet()

        val primaryKeyColumns = tableLevelPk + inlinePk
        val columns = createTable.columnDefinitions.map { toColumn(it, primaryKeyColumns) }

        val foreignKeys = allIndexes.filterIsInstance<ForeignKeyIndex>().mapIndexed { i, fk ->
            ForeignKey(
                name = fk.name ?: "${createTable.table.name}_fk_$i",
                columns = fk.columnsNames.orEmpty(),
                referencedTable = fk.table.name,
                referencedColumns = fk.referencedColumnNames.orEmpty()
            )
        }

        val tableLevelUnique = allIndexes
            .filterIsInstance<Index>()
            .filter { it !is ForeignKeyIndex && it.type == "UNIQUE" }
            .mapIndexed { i, idx ->
                UniqueConstraint(name = idx.name ?: "${createTable.table.name}_uk_$i", columns = idx.columnsNames.orEmpty())
            }

        val inlineUnique = createTable.columnDefinitions
            .filter { colDef -> colDef.columnSpecs?.any { it.uppercase() == "UNIQUE" } == true }
            .map { UniqueConstraint(name = "${createTable.table.name}_${it.columnName}_uk", columns = listOf(it.columnName)) }

        return Table(
            name = createTable.table.name,
            schema = createTable.table.schemaName,
            columns = columns,
            foreignKeys = foreignKeys,
            uniqueConstraints = tableLevelUnique + inlineUnique
        )
    }

    private fun hasSpecPair(colDef: ColumnDefinition, first: String, second: String): Boolean {
        val specs = colDef.columnSpecs?.map { it.uppercase() } ?: emptyList()
        return specs.zipWithNext().any { it == first to second }
    }

    private fun toColumn(colDef: ColumnDefinition, primaryKeyColumns: Set<String>): Column {
        val sqlType = colDef.colDataType.dataType.uppercase()
        val length = colDef.colDataType.argumentsStringList?.firstOrNull()?.toIntOrNull()
        val notNullDeclared = hasSpecPair(colDef, "NOT", "NULL")
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