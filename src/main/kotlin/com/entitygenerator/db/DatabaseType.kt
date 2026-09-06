package com.entitygenerator.db

enum class DatabaseType(val label: String, val driverClassName: String, val defaultPort: Int) {
    POSTGRESQL("PostgreSQL", "org.postgresql.Driver", 5432),
    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", 3306),
    H2("H2", "org.h2.Driver", 9092);

    override fun toString(): String = label

    fun buildUrl(host: String, port: Int, database: String, useSsl: Boolean): String {
        val base = when (this) {
            POSTGRESQL -> "jdbc:postgresql://$host:$port/$database"
            MYSQL -> "jdbc:mysql://$host:$port/$database"
            H2 -> "jdbc:h2:tcp://$host:$port/$database"
        }
        if (!useSsl || this == H2) return base
        return when (this) {
            POSTGRESQL -> "$base?sslmode=require"
            MYSQL -> "$base?sslMode=REQUIRED"
            else -> base
        }
    }
}