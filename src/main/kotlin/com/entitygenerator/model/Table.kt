package com.entitygenerator.model

data class Table(
    val name: String,
    val columns: List<Column>
) {
    val primaryKeyColumns: List<Column> get() = columns.filter { it.isPrimaryKey }
}