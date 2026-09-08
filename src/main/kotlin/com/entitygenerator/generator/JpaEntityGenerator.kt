package com.entitygenerator.generator

import com.entitygenerator.model.Column
import com.entitygenerator.model.ForeignKey
import com.entitygenerator.model.Table
import com.entitygenerator.util.toCamelCase
import com.entitygenerator.util.toPascalCase

data class GeneratedFile(val className: String, val source: String)

class JpaEntityGenerator(private val basePackage: String) {

    fun generateAll(tables: List<Table>, nameOverride: String? = null, baseClassName: String? = null): List<GeneratedFile> {
        val classNameByTable: Map<String, String> = tables.associate { table ->
            val raw = if (tables.size == 1 && nameOverride != null) nameOverride else table.name
            table.name to raw.toPascalCase()
        }

        val incomingReferences: Map<String, List<Pair<Table, ForeignKey>>> =
            tables.flatMap { owner -> owner.foreignKeys.map { fk -> fk.referencedTable to (owner to fk) } }
                .groupBy({ it.first }, { it.second })

        return tables.flatMap { table ->
            val className = classNameByTable.getValue(table.name)
            val refsToThisTable = incomingReferences[table.name].orEmpty()

            if (table.primaryKeyColumns.size > 1) {
                val idClassName = "${className}Id"
                listOf(
                    buildEmbeddedIdFile(idClassName, table.primaryKeyColumns),
                    buildEntityFile(table, className, idClassName, classNameByTable, refsToThisTable, baseClassName)
                )
            } else {
                listOf(buildEntityFile(table, className, idClassName = null, classNameByTable, refsToThisTable, baseClassName))
            }
        }
    }

    private fun buildEntityFile(
        table: Table,
        className: String,
        idClassName: String?,
        classNameByTable: Map<String, String>,
        incomingReferences: List<Pair<Table, ForeignKey>>,
        baseClassName: String?
    ): GeneratedFile {
        val imports = sortedSetOf(
            "jakarta.persistence.Column",
            "jakarta.persistence.Entity",
            "jakarta.persistence.Table",
            "lombok.AllArgsConstructor",
            "lombok.Getter",
            "lombok.NoArgsConstructor",
            "lombok.Setter"
        )

        val hasBaseClass = !baseClassName.isNullOrBlank()
        val builderAnnotation = if (hasBaseClass) {
            imports += "lombok.experimental.SuperBuilder"
            "@SuperBuilder"
        } else {
            imports += "lombok.Builder"
            "@Builder"
        }

        val pkColumnNames = table.primaryKeyColumns.map { it.name }.toSet()
        val relationshipFks = table.foreignKeys.filter { fk -> fk.columns.none { it in pkColumnNames } }
        val relationshipColumnNames = relationshipFks.flatMap { it.columns }.toSet()

        val members = mutableListOf<String>()

        if (idClassName != null) {
            imports += "jakarta.persistence.EmbeddedId"
            members += renderField(listOf("@EmbeddedId"), idClassName, "id")
        }

        table.columns.forEach { column ->
            if (idClassName != null && pkColumnNames.contains(column.name)) return@forEach
            if (column.name in relationshipColumnNames) return@forEach

            val annotations = mutableListOf<String>()
            if (idClassName == null && column.isPrimaryKey) {
                imports += "jakarta.persistence.Id"
                imports += "jakarta.persistence.GeneratedValue"
                imports += "jakarta.persistence.GenerationType"
                annotations += "@Id"
                annotations += "@GeneratedValue(strategy = GenerationType.IDENTITY)"
            }
            annotations += columnAnnotation(column)

            val type = resolveJavaType(column)
            type.importFqcn?.let { imports += it }
            members += renderField(annotations, type.simpleName, column.name.toCamelCase())
        }

        relationshipFks.forEach { fk ->
            val referencedClassName = classNameByTable[fk.referencedTable]
            if (referencedClassName == null) {
                fk.columns.forEach { colName ->
                    table.columns.firstOrNull { it.name == colName }?.let { column ->
                        val type = resolveJavaType(column)
                        type.importFqcn?.let { imports += it }
                        members += renderField(listOf(columnAnnotation(column)), type.simpleName, column.name.toCamelCase())
                    }
                }
                return@forEach
            }

            imports += "jakarta.persistence.ManyToOne"
            imports += "jakarta.persistence.JoinColumn"
            val fieldName = relationshipFieldName(fk)
            val joinAnnotation = joinColumnAnnotation(fk, table.columns, imports)
            members += renderField(listOf("@ManyToOne", joinAnnotation), referencedClassName, fieldName)
        }

        incomingReferences.forEach { (owningTable, fk) ->
            val owningClassName = classNameByTable[owningTable.name] ?: return@forEach
            imports += "jakarta.persistence.OneToMany"
            imports += "java.util.List"
            val owningFieldName = relationshipFieldName(fk)
            val collectionFieldName = pluralize(owningClassName.replaceFirstChar { it.lowercase() })
            members += renderField(
                listOf("@OneToMany(mappedBy = \"$owningFieldName\")"),
                "List<$owningClassName>",
                collectionFieldName
            )
        }

        val classDeclaration = classDeclarationLine(className, baseClassName, imports)

        val source = buildString {
            appendLine("package $basePackage;")
            appendLine()
            imports.forEach { appendLine("import $it;") }
            appendLine()
            appendLine("@Getter")
            appendLine("@Setter")
            appendLine("@NoArgsConstructor")
            appendLine("@AllArgsConstructor")
            appendLine(builderAnnotation)
            appendLine("@Entity")
            appendLine(tableAnnotation(table))
            appendLine(classDeclaration)
            appendLine()
            append(members.joinToString("\n\n"))
            appendLine()
            appendLine()
            append("}")
        }

        return GeneratedFile(className, source)
    }

    private fun buildEmbeddedIdFile(className: String, pkColumns: List<Column>): GeneratedFile {
        val imports = sortedSetOf(
            "jakarta.persistence.Column",
            "jakarta.persistence.Embeddable",
            "lombok.AllArgsConstructor",
            "lombok.Builder",
            "lombok.EqualsAndHashCode",
            "lombok.Getter",
            "lombok.NoArgsConstructor",
            "lombok.Setter",
            "java.io.Serializable"
        )

        val members = pkColumns.map { column ->
            val type = resolveJavaType(column)
            type.importFqcn?.let { imports += it }
            renderField(listOf(columnAnnotation(column)), type.simpleName, column.name.toCamelCase())
        }

        val source = buildString {
            appendLine("package $basePackage;")
            appendLine()
            imports.forEach { appendLine("import $it;") }
            appendLine()
            appendLine("@Getter")
            appendLine("@Setter")
            appendLine("@NoArgsConstructor")
            appendLine("@AllArgsConstructor")
            appendLine("@Builder")
            appendLine("@EqualsAndHashCode")
            appendLine("@Embeddable")
            appendLine("public class $className implements Serializable {")
            appendLine()
            append(members.joinToString("\n\n"))
            appendLine()
            appendLine()
            append("}")
        }

        return GeneratedFile(className, source)
    }

    private fun classDeclarationLine(className: String, baseClassName: String?, imports: MutableSet<String>): String {
        if (baseClassName.isNullOrBlank()) return "public class $className {"
        val simpleName = if (baseClassName.contains(".")) {
            imports += baseClassName
            baseClassName.substringAfterLast(".")
        } else {
            baseClassName
        }
        return "public class $className extends $simpleName {"
    }

    private fun renderField(annotations: List<String>, type: String, fieldName: String): String =
        buildString {
            annotations.forEach { appendLine("    $it") }
            append("    private $type $fieldName;")
        }

    private fun columnAnnotation(column: Column): String {
        val members = mutableListOf("name = \"${column.name}\"")
        if (!column.nullable) members += "nullable = false"
        column.length?.let { members += "length = $it" }
        return "@Column(${members.joinToString(", ")})"
    }

    private fun tableAnnotation(table: Table): String {
        val members = mutableListOf("name = \"${table.name}\"")
        if (!table.schema.isNullOrBlank()) {
            members += "schema = \"${table.schema}\""
        }
        if (table.uniqueConstraints.isNotEmpty()) {
            val constraints = table.uniqueConstraints.joinToString(", ") { uc ->
                val cols = uc.columns.joinToString(", ") { "\"$it\"" }
                "@UniqueConstraint(columnNames = {$cols})"
            }
            members += "uniqueConstraints = {$constraints}"
        }
        return "@Table(${members.joinToString(", ")})"
    }

    private fun joinColumnAnnotation(fk: ForeignKey, tableColumns: List<Column>, imports: MutableSet<String>): String {
        val allNullable = fk.columns.all { colName -> tableColumns.firstOrNull { it.name == colName }?.nullable ?: true }

        if (fk.columns.size == 1) {
            val members = mutableListOf("name = \"${fk.columns.first()}\"")
            if (!allNullable) members += "nullable = false"
            return "@JoinColumn(${members.joinToString(", ")})"
        }

        imports += "jakarta.persistence.JoinColumns"
        val joins = fk.columns.mapIndexed { i, colName ->
            val refCol = fk.referencedColumns.getOrElse(i) { colName }
            "@JoinColumn(name = \"$colName\", referencedColumnName = \"$refCol\")"
        }.joinToString(", ")
        return "@JoinColumns({$joins})"
    }

    private fun relationshipFieldName(fk: ForeignKey): String {
        if (fk.columns.size == 1) {
            val col = fk.columns.first()
            val stripped = when {
                col.endsWith("_id", ignoreCase = true) -> col.dropLast(3)
                col.endsWith("Id") -> col.dropLast(2)
                else -> col
            }
            return stripped.toCamelCase()
        }
        return fk.referencedTable.toCamelCase()
    }

    private fun pluralize(word: String): String = when {
        word.endsWith("s") -> word // assume already plural -- matches typical DB naming ("orders", "customers")
        word.endsWith("y") && word.length > 1 && word[word.length - 2].lowercaseChar() !in "aeiou" -> word.dropLast(1) + "ies"
        word.endsWith("x") || word.endsWith("z") || word.endsWith("ch") || word.endsWith("sh") -> word + "es"
        else -> word + "s"
    }

    private data class JavaType(val simpleName: String, val importFqcn: String?)

    private fun resolveJavaType(column: Column): JavaType = when (column.javaType) {
        "Long" -> JavaType("Long", null)
        "Integer" -> JavaType("Integer", null)
        "Boolean" -> JavaType("Boolean", null)
        "BigDecimal" -> JavaType("BigDecimal", "java.math.BigDecimal")
        "LocalDate" -> JavaType("LocalDate", "java.time.LocalDate")
        "LocalDateTime" -> JavaType("LocalDateTime", "java.time.LocalDateTime")
        else -> JavaType("String", null)
    }
}