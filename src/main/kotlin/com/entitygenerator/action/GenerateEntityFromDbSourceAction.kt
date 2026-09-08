package com.entitygenerator.action

import com.entitygenerator.db.JdbcSchemaReader
import com.entitygenerator.licensing.LicenseChecker
import com.entitygenerator.ui.ConnectionPickerDialog
import com.entitygenerator.ui.TableSelectionDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class GenerateEntityFromDbSourceAction : DirectoryTargetingAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val directory = resolveDirectory(e) ?: return

        if (!LicenseChecker.isProLicensed()) {
            Messages.showInfoMessage(
                project,
                "Generating entities from a live database connection is a Pro feature. " +
                        "Visit the plugin's JetBrains Marketplace page to upgrade.",
                "Pro Feature"
            )
            return
        }

        val pickerDialog = ConnectionPickerDialog(project)
        if (!pickerDialog.showAndGet()) return
        val connection = pickerDialog.connection ?: return

        connection.use {
            val reader = JdbcSchemaReader(it)
            val tableRefs = reader.listTables()
            if (tableRefs.isEmpty()) {
                Messages.showInfoMessage(project, "No tables found in this database.", "Generate Entity")
                return
            }

            val tableDialog = TableSelectionDialog(project, tableRefs)
            if (!tableDialog.showAndGet()) return

            val baseClassName = Messages.showInputDialog(
                project,
                "Base class to extend (optional, e.g. com.example.BaseEntity):",
                "Generate Entity",
                null
            )

            val tables = tableDialog.selectedTables.map { ref -> reader.readTable(ref) }
            EntityGenerationRunner.runForTables(
                project = project,
                directory = directory,
                tables = tables,
                requestedName = null,
                baseClassName = baseClassName?.takeIf { it.isNotBlank() }
            )
        }
    }
}