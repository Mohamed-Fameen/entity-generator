package com.entitygenerator.action

import com.entitygenerator.generator.JpaEntityGenerator
import com.entitygenerator.model.Table
import com.entitygenerator.parser.DdlParser
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory
import java.io.File

object EntityGenerationRunner {

    fun runFromDdl(
        project: Project,
        directory: PsiDirectory,
        ddl: String,
        requestedName: String?,
        baseClassName: String? = null
    ) {
        val tables = DdlParser().parse(ddl)
        if (tables.isEmpty()) {
            Messages.showErrorDialog(project, "No CREATE TABLE statement found in the provided DDL.", "Generate Entity")
            return
        }
        runForTables(project, directory, tables, requestedName, baseClassName)
    }

    fun runForTables(
        project: Project,
        directory: PsiDirectory,
        tables: List<Table>,
        requestedName: String?,
        baseClassName: String? = null
    ) {
        val basePackage = JavaDirectoryService.getInstance().getPackage(directory)?.qualifiedName.orEmpty()
        val generator = JpaEntityGenerator(basePackage)
        val targetDir = File(directory.virtualFile.path)

        val generatedFiles = generator.generateAll(tables, requestedName, baseClassName)
        generatedFiles.forEach { file ->
            File(targetDir, "${file.className}.java").writeText(file.source)
        }

        LocalFileSystem.getInstance().refreshIoFiles(listOf(targetDir))
        Messages.showInfoMessage(
            project,
            "Generated ${generatedFiles.size} Java class(es) from ${tables.size} table(s) in ${directory.virtualFile.path}",
            "Generate Entity"
        )
    }
}