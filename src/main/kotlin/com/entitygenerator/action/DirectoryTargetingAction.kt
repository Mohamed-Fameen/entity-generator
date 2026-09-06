package com.entitygenerator.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiDirectory

abstract class DirectoryTargetingAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = resolveDirectory(e) != null
    }

    protected fun resolveDirectory(e: AnActionEvent): PsiDirectory? =
        e.getData(CommonDataKeys.PSI_ELEMENT) as? PsiDirectory
}