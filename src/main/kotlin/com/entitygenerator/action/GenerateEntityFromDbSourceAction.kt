package com.entitygenerator.action

import com.entitygenerator.db.JdbcSchemaReader
import com.entitygenerator.ui.DbConnectionDialog
import com.entitygenerator.ui.TableSelectionDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class GenerateEntityFromDbSourceAction : DirectoryTargetingAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val directory = resolveDirectory(e) ?: return

        val connectionDialog = DbConnectionDialog(project)
        if (!connectionDialog.showAndGet()) return
        val connection = connectionDialog.connection ?: return

        connection.use {
            val reader = JdbcSchemaReader(it)
            val tableNames = reader.listTables()
            if (tableNames.isEmpty()) {
                Messages.showInfoMessage(project, "No tables found in this database.", "Generate Entity")
                return
            }

            val tableDialog = TableSelectionDialog(project, tableNames)
            if (!tableDialog.showAndGet()) return

            val tables = tableDialog.selectedTables.map { name -> reader.readTable(name) }
            EntityGenerationRunner.runForTables(project, directory, tables, requestedName = null)
        }
    }
}