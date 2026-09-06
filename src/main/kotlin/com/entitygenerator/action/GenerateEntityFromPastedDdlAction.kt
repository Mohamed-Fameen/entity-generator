package com.entitygenerator.action

import com.entitygenerator.ui.PasteDdlDialog
import com.intellij.openapi.actionSystem.AnActionEvent

class GenerateEntityFromPastedDdlAction : DirectoryTargetingAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val directory = resolveDirectory(e) ?: return

        val dialog = PasteDdlDialog(project)
        if (dialog.showAndGet()) {
            EntityGenerationRunner.runFromDdl(
                project = project,
                directory = directory,
                ddl = dialog.ddlText,
                requestedName = dialog.entityName.takeIf { it.isNotBlank() }
            )
        }
    }
}