package com.entitygenerator.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.VfsUtilCore

class GenerateEntityFromSqlFileAction : DirectoryTargetingAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val directory = resolveDirectory(e) ?: return

        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withFileFilter { it.extension.equals("sql", ignoreCase = true) }
            .withTitle("Select DDL (.sql) File")

        val file = FileChooser.chooseFile(descriptor, project, null) ?: return
        val ddl = VfsUtilCore.loadText(file)
        EntityGenerationRunner.runFromDdl(project, directory, ddl, requestedName = null)
    }
}