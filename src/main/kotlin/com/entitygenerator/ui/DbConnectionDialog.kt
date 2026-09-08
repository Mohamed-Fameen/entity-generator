package com.entitygenerator.ui

import com.entitygenerator.db.ConnectionCredentialsStore
import com.entitygenerator.db.ConnectionStorageService
import com.entitygenerator.db.DatabaseType
import com.entitygenerator.db.JdbcConnectionFactory
import com.entitygenerator.db.SavedConnection
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.sql.Connection
import java.util.UUID
import javax.swing.JComponent

class DbConnectionDialog(
    private val project: Project,
    private val existing: SavedConnection? = null
) : DialogWrapper(project) {

    private var selectedType: DatabaseType = existing?.type ?: DatabaseType.POSTGRESQL

    private val labelField = JBTextField(existing?.label ?: "")
    private val hostField = JBTextField(existing?.host ?: "localhost")
    private val portField = JBTextField((existing?.port ?: selectedType.defaultPort).toString())
    private val databaseField = JBTextField(existing?.database ?: "")
    private val userField = JBTextField(existing?.username ?: "")
    private val passwordField = JBPasswordField().apply {
        existing?.let { text = ConnectionCredentialsStore.loadPassword(it.id) ?: "" }
    }
    private val sslCheckBox = JBCheckBox("Use SSL (recommended for RDS / remote databases)").apply {
        isSelected = existing?.useSsl ?: false
    }

    var connection: Connection? = null
        private set

    init {
        title = if (existing != null) "Edit Connection" else "New Connection"
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Connection name:") { cell(labelField).align(AlignX.FILL) }
        row("Database type:") {
            comboBox(DatabaseType.entries.toList())
                .applyToComponent {
                    selectedItem = selectedType
                    addActionListener {
                        selectedType = selectedItem as DatabaseType
                        if (existing == null) portField.text = selectedType.defaultPort.toString()
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
        val label = labelField.text.trim()
        if (label.isBlank()) {
            setErrorText("Connection name is required", labelField)
            return
        }
        val port = portField.text.trim().toIntOrNull()
        if (port == null) {
            setErrorText("Port must be a number", portField)
            return
        }

        val url = selectedType.buildUrl(hostField.text.trim(), port, databaseField.text.trim(), sslCheckBox.isSelected)

        try {
            connection = JdbcConnectionFactory.connect(
                type = selectedType,
                url = url,
                username = userField.text.trim(),
                password = String(passwordField.password)
            )

            val id = existing?.id ?: UUID.randomUUID().toString()
            ConnectionStorageService.getInstance().save(
                SavedConnection(
                    id = id,
                    label = label,
                    type = selectedType,
                    host = hostField.text.trim(),
                    port = port,
                    database = databaseField.text.trim(),
                    username = userField.text.trim(),
                    useSsl = sslCheckBox.isSelected
                )
            )
            ConnectionCredentialsStore.save(id, userField.text.trim(), String(passwordField.password))

            super.doOKAction()
        } catch (ex: Exception) {
            setErrorText(ex.message ?: "Failed to connect", hostField)
        }
    }
}