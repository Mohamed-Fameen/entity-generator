package com.entitygenerator.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import java.awt.Dimension
import javax.swing.JComponent

class TableSelectionDialog(project: Project, private val tableNames: List<String>) : DialogWrapper(project) {

    private val checkBoxList = CheckBoxList<String>().apply {
        tableNames.forEach { addItem(it, it, false) }
    }

    val selectedTables: List<String>
        get() = tableNames.indices.filter { checkBoxList.isItemSelected(it) }.map { tableNames[it] }

    init {
        title = "Select Tables"
        init()
    }

    override fun createCenterPanel(): JComponent =
        JBScrollPane(checkBoxList).apply { preferredSize = Dimension(400, 350) }

    override fun doValidate(): ValidationInfo? =
        if (selectedTables.isEmpty()) ValidationInfo("Select at least one table", checkBoxList) else null
}