package com.entitygenerator.ui

import com.entitygenerator.db.ConnectionCredentialsStore
import com.entitygenerator.db.ConnectionStorageService
import com.entitygenerator.db.JdbcConnectionFactory
import com.entitygenerator.db.SavedConnection
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import java.sql.Connection
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

class ConnectionPickerDialog(private val project: Project) : DialogWrapper(project) {

    private val storage = ConnectionStorageService.getInstance()
    private val listModel = DefaultListModel<SavedConnection>()
    private val connectionList = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = ListCellRenderer<SavedConnection> { list, value, _, isSelected, _ ->
            JLabel("${value.label}   (${value.type.label} \u2014 ${value.host})").apply {
                isOpaque = true
                background = if (isSelected) list.selectionBackground else list.background
                foreground = if (isSelected) list.selectionForeground else list.foreground
                border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
            }
        }
    }

    var connection: Connection? = null
        private set

    init {
        title = "Connect to Database"
        setOKButtonText("Connect")
        refreshList()
        init()
    }

    private fun refreshList() {
        val previouslySelected = connectionList.selectedValue
        listModel.clear()
        storage.list().forEach { listModel.addElement(it) }
        previouslySelected?.let { prev ->
            (0 until listModel.size()).firstOrNull { listModel.get(it).id == prev.id }
                ?.let { connectionList.selectedIndex = it }
        }
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            cell(JBScrollPane(connectionList).apply { preferredSize = Dimension(420, 260) }).align(AlignX.FILL)
        }
        row {
            button("New Connection...") { openNewConnection() }
            button("Edit...") { openEditConnection() }
            button("Delete") { deleteSelected() }
        }
    }

    override fun doOKAction() {
        val selected = connectionList.selectedValue
        if (selected == null) {
            setErrorText("Select a connection, or create a new one")
            return
        }
        try {
            val password = ConnectionCredentialsStore.loadPassword(selected.id).orEmpty()
            connection = JdbcConnectionFactory.connect(
                type = selected.type,
                url = selected.type.buildUrl(selected.host, selected.port, selected.database, selected.useSsl),
                username = selected.username,
                password = password
            )
            super.doOKAction()
        } catch (ex: Exception) {
            setErrorText(ex.message ?: "Failed to connect")
        }
    }

    private fun openNewConnection() {
        val dialog = DbConnectionDialog(project)
        if (dialog.showAndGet()) {
            connection = dialog.connection
            close(OK_EXIT_CODE)
        }
    }

    private fun openEditConnection() {
        val selected = connectionList.selectedValue ?: return
        val dialog = DbConnectionDialog(project, existing = selected)
        if (dialog.showAndGet()) {
            refreshList()
        }
    }

    private fun deleteSelected() {
        val selected = connectionList.selectedValue ?: return
        storage.delete(selected.id)
        ConnectionCredentialsStore.delete(selected.id)
        refreshList()
    }
}