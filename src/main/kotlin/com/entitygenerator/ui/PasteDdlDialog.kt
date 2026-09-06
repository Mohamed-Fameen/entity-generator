package com.entitygenerator.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import javax.swing.JComponent

class PasteDdlDialog(project: Project) : DialogWrapper(project) {

    private val ddlArea = JBTextArea().apply {
        rows = 20
        emptyText.text = "Paste your CREATE TABLE statement(s) here..."
    }
    private val nameField = JBTextField()

    val ddlText: String get() = ddlArea.text
    val entityName: String get() = nameField.text.trim()

    init {
        title = "Generate Entity from DDL"
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Entity name (optional):") {
            cell(nameField).align(AlignX.FILL)
        }
        row {
            cell(JBScrollPane(ddlArea).apply { preferredSize = Dimension(640, 420) })
                .align(AlignX.FILL)
        }
    }

    override fun doValidate(): ValidationInfo? =
        if (ddlText.isBlank()) ValidationInfo("DDL cannot be empty", ddlArea) else null
}