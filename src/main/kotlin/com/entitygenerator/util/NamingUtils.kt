package com.entitygenerator.util

fun String.toPascalCase(): String =
    split('_', '-').joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }

fun String.toCamelCase(): String =
    toPascalCase().replaceFirstChar { it.lowercase() }