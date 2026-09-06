package com.entitygenerator.ui

import com.entitygenerator.db.DatabaseType
import com.entitygenerator.db.JdbcConnectionFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.sql.Connection
import javax.swing.JComponent

class DbConnectionDialog(project: Project) : DialogWrapper(project) {

    private var selectedType: DatabaseType = DatabaseType.POSTGRESQL

    private val hostField = JBTextField("localhost")
    private val portField = JBTextField(selectedType.defaultPort.toString())
    private val databaseField = JBTextField()
    private val userField = JBTextField()
    private val passwordField = JBPasswordField()
    private val sslCheckBox = JBCheckBox("Use SSL (recommended for RDS / remote databases)")

    var connection: Connection? = null
        private set

    init {
        title = "Connect to Database"
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Database type:") {
            comboBox(DatabaseType.entries.toList())
                .applyToComponent {
                    addActionListener {
                        selectedType = selectedItem as DatabaseType
                        portField.text = selectedType.defaultPort.toString()
                    }
                }
        }
        row("Host:") { cell(hostField).align(AlignX.FILL) }
        row("Port:") { cell(portField).align(AlignX.FILL) }
        row("Database name:") { cell(databaseField).align(AlignX.FILL) }
        row("Username:") { cell(userField).align(AlignX.FILL) }
        row("Password:") { cell(passwordField).align(AlignX.FILL) }
        row { cell(sslCheckBox) }
    }

    override fun doOKAction() {
        val port = portField.text.trim().toIntOrNull()
        if (port == null) {
            setErrorText("Port must be a number", portField)
            return
        }

        val url = selectedType.buildUrl(
            host = hostField.text.trim(),
            port = port,
            database = databaseField.text.trim(),
            useSsl = sslCheckBox.isSelected
        )

        try {
            connection = JdbcConnectionFactory.connect(
                type = selectedType,
                url = url,
                username = userField.text.trim(),
                password = String(passwordField.password)
            )
            super.doOKAction()
        } catch (ex: Exception) {
            setErrorText(ex.message ?: "Failed to connect", hostField)
        }
    }
}