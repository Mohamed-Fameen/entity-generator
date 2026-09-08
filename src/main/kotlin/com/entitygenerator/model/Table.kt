package com.entitygenerator.model

data class Table(
    val name: String,
    val schema: String? = null,
    val columns: List<Column>,
    val foreignKeys: List<ForeignKey> = emptyList(),
    val uniqueConstraints: List<UniqueConstraint> = emptyList()
) {
    val primaryKeyColumns: List<Column> get() = columns.filter { it.isPrimaryKey }
}