package com.entitygenerator.ui

import com.entitygenerator.db.TableRef
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class TableSelectionDialog(project: Project, private val allTables: List<TableRef>) : DialogWrapper(project) {

    private val checkedTables = mutableSetOf<TableRef>()
    private var displayedItems: List<TableRef> = emptyList()
    private val searchField = JBTextField()
    private var checkBoxList = CheckBoxList<TableRef>()
    private val scrollPane = JBScrollPane(checkBoxList).apply { preferredSize = Dimension(420, 350) }

    val selectedTables: List<TableRef> get() = checkedTables.toList()

    init {
        title = "Select Tables"
        init()
        rebuildList(allTables)

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = applyFilter()
            override fun removeUpdate(e: DocumentEvent) = applyFilter()
            override fun changedUpdate(e: DocumentEvent) = applyFilter()
        })
    }

    private fun applyFilter() {
        captureChecked()
        val query = searchField.text.trim().lowercase()
        val filtered = if (query.isBlank()) {
            allTables
        } else {
            allTables.filter { "${it.schema}.${it.name}".lowercase().contains(query) }
        }
        rebuildList(filtered)
    }

    private fun captureChecked() {
        displayedItems.forEachIndexed { index, ref ->
            if (checkBoxList.isItemSelected(index)) checkedTables += ref else checkedTables -= ref
        }
    }

    private fun rebuildList(items: List<TableRef>) {
        val sorted = items.sortedWith(compareBy({ it.schema }, { it.name }))
        displayedItems = sorted

        val newList = CheckBoxList<TableRef>()
        sorted.forEach { ref -> newList.addItem(ref, "${ref.schema}.${ref.name}", checkedTables.contains(ref)) }
        checkBoxList = newList
        scrollPane.setViewportView(newList)
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Search:") { cell(searchField).align(AlignX.FILL) }
        row {
            cell(scrollPane).align(AlignX.FILL)
        }
    }

    override fun doValidate(): ValidationInfo? {
        captureChecked()
        return if (checkedTables.isEmpty()) ValidationInfo("Select at least one table", scrollPane) else null
    }
}