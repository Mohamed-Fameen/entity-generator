package com.entitygenerator.db

import java.sql.Connection
import java.sql.SQLException
import java.util.Properties

object JdbcConnectionFactory {

    fun connect(type: DatabaseType, url: String, username: String, password: String): Connection {
        // Instantiate the driver directly via this plugin's own classloader rather than relying
        // on DriverManager's ServiceLoader auto-discovery, which can silently miss drivers
        // bundled inside an IntelliJ plugin.
        val driverClass = Class.forName(type.driverClassName, true, this::class.java.classLoader)
        val driver = driverClass.getDeclaredConstructor().newInstance() as java.sql.Driver

        val props = Properties().apply {
            setProperty("user", username)
            setProperty("password", password)
        }

        return driver.connect(url, props)
            ?: throw SQLException("Driver did not accept the URL: $url")
    }
}