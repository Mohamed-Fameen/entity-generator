package com.entitygenerator.model

data class ForeignKey(
    val name: String,
    val columns: List<String>,          // column(s) in this table, order matches referencedColumns
    val referencedTable: String,
    val referencedColumns: List<String>
)

data class UniqueConstraint(
    val name: String,
    val columns: List<String>
)