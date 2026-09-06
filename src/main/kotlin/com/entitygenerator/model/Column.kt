package com.entitygenerator.model

data class Column(
    val name: String,
    val sqlType: String,
    val javaType: String,
    val nullable: Boolean,
    val isPrimaryKey: Boolean,
    val length: Int? = null
)