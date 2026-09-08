package com.entitygenerator.db

data class SavedConnection(
    var id: String = "",
    var label: String = "",
    var type: DatabaseType = DatabaseType.POSTGRESQL,
    var host: String = "",
    var port: Int = 0,
    var database: String = "",
    var username: String = "",
    var useSsl: Boolean = false
)