package com.entitygenerator.generator

import com.entitygenerator.model.Column
import com.entitygenerator.model.Table
import com.entitygenerator.util.toCamelCase
import com.entitygenerator.util.toPascalCase
import com.squareup.javapoet.*
import javax.lang.model.element.Modifier

class JpaEntityGenerator(private val basePackage: String) {

    private val jpa = "jakarta.persistence" // switch to "javax.persistence" for older stacks

    /**
     * Returns one JavaFile for a simple entity, or two (embeddable ID class + entity)
     * when the table has a composite (multi-column) primary key.
     */
    fun generate(table: Table, classNameOverride: String? = null): List<JavaFile> {
        val className = (classNameOverride ?: table.name).toPascalCase()
        val pkColumns = table.primaryKeyColumns

        return if (pkColumns.size > 1) {
            val idClassName = "${className}Id"
            val idClass = buildEmbeddedIdClass(idClassName, pkColumns)
            val entityClass = buildEntityClass(className, table, idClassName)
            listOf(
                JavaFile.builder(basePackage, idClass).build(),
                JavaFile.builder(basePackage, entityClass).build()
            )
        } else {
            listOf(JavaFile.builder(basePackage, buildEntityClass(className, table, idClassName = null)).build())
        }
    }

    private fun buildEntityClass(className: String, table: Table, idClassName: String?): TypeSpec {
        val classBuilder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get(jpa, "Entity")).build())
            .addAnnotation(
                AnnotationSpec.builder(ClassName.get(jpa, "Table"))
                    .addMember("name", "\$S", table.name)
                    .build()
            )

        val pkColumnNames = table.primaryKeyColumns.map { it.name }.toSet()

        if (idClassName != null) {
            val idType = ClassName.get(basePackage, idClassName)
            classBuilder.addField(
                FieldSpec.builder(idType, "id", Modifier.PRIVATE)
                    .addAnnotation(ClassName.get(jpa, "EmbeddedId"))
                    .build()
            )
            classBuilder.addMethod(getter("id", idType))
            classBuilder.addMethod(setter("id", idType))
        }

        table.columns.forEach { column ->
            if (idClassName != null && pkColumnNames.contains(column.name)) return@forEach // lives in the embedded ID class instead

            val fieldName = column.name.toCamelCase()
            val type = resolveJavaType(column)
            val fieldBuilder = FieldSpec.builder(type, fieldName, Modifier.PRIVATE)

            if (idClassName == null && column.isPrimaryKey) {
                fieldBuilder.addAnnotation(ClassName.get(jpa, "Id"))
                fieldBuilder.addAnnotation(
                    AnnotationSpec.builder(ClassName.get(jpa, "GeneratedValue"))
                        .addMember("strategy", "\$T.IDENTITY", ClassName.get(jpa, "GenerationType"))
                        .build()
                )
            }
            fieldBuilder.addAnnotation(columnAnnotation(column))

            classBuilder.addField(fieldBuilder.build())
            classBuilder.addMethod(getter(fieldName, type))
            classBuilder.addMethod(setter(fieldName, type))
        }

        return classBuilder.build()
    }

    private fun buildEmbeddedIdClass(className: String, pkColumns: List<Column>): TypeSpec {
        val builder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(ClassName.get("java.io", "Serializable"))
            .addAnnotation(ClassName.get(jpa, "Embeddable"))

        pkColumns.forEach { column ->
            val fieldName = column.name.toCamelCase()
            val type = resolveJavaType(column)
            builder.addField(
                FieldSpec.builder(type, fieldName, Modifier.PRIVATE)
                    .addAnnotation(columnAnnotation(column))
                    .build()
            )
            builder.addMethod(getter(fieldName, type))
            builder.addMethod(setter(fieldName, type))
        }

        builder.addMethod(buildEqualsMethod(className, pkColumns))
        builder.addMethod(buildHashCodeMethod(pkColumns))

        return builder.build()
    }

    private fun buildEqualsMethod(idClassName: String, pkColumns: List<Column>): MethodSpec {
        val selfClass = ClassName.get(basePackage, idClassName)
        val objects = ClassName.get("java.util", "Objects")

        val comparison = CodeBlock.builder()
        pkColumns.forEachIndexed { index, column ->
            val fieldName = column.name.toCamelCase()
            if (index > 0) comparison.add(" && ")
            comparison.add("\$T.equals(this.\$N, that.\$N)", objects, fieldName, fieldName)
        }

        return MethodSpec.methodBuilder("equals")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.BOOLEAN)
            .addParameter(TypeName.OBJECT, "o")
            .beginControlFlow("if (this == o)")
            .addStatement("return true")
            .endControlFlow()
            .beginControlFlow("if (o == null || getClass() != o.getClass())")
            .addStatement("return false")
            .endControlFlow()
            .addStatement("\$T that = (\$T) o", selfClass, selfClass)
            .addStatement("return \$L", comparison.build())
            .build()
    }

    private fun buildHashCodeMethod(pkColumns: List<Column>): MethodSpec {
        val objects = ClassName.get("java.util", "Objects")
        val args = CodeBlock.builder()
        pkColumns.forEachIndexed { index, column ->
            if (index > 0) args.add(", ")
            args.add("\$N", column.name.toCamelCase())
        }

        return MethodSpec.methodBuilder("hashCode")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.INT)
            .addStatement("return \$T.hash(\$L)", objects, args.build())
            .build()
    }

    private fun columnAnnotation(column: Column): AnnotationSpec {
        val builder = AnnotationSpec.builder(ClassName.get(jpa, "Column"))
            .addMember("name", "\$S", column.name)
        if (!column.nullable) builder.addMember("nullable", "false")
        column.length?.let { builder.addMember("length", "\$L", it) }
        return builder.build()
    }

    private fun resolveJavaType(column: Column): ClassName = when (column.javaType) {
        "Long" -> ClassName.get("java.lang", "Long")
        "Integer" -> ClassName.get("java.lang", "Integer")
        "Boolean" -> ClassName.get("java.lang", "Boolean")
        "BigDecimal" -> ClassName.get("java.math", "BigDecimal")
        "LocalDate" -> ClassName.get("java.time", "LocalDate")
        "LocalDateTime" -> ClassName.get("java.time", "LocalDateTime")
        else -> ClassName.get("java.lang", "String")
    }

    private fun getter(fieldName: String, type: TypeName) =
        MethodSpec.methodBuilder("get" + fieldName.replaceFirstChar { it.uppercase() })
            .addModifiers(Modifier.PUBLIC)
            .returns(type)
            .addStatement("return this.\$N", fieldName)
            .build()

    private fun setter(fieldName: String, type: TypeName) =
        MethodSpec.methodBuilder("set" + fieldName.replaceFirstChar { it.uppercase() })
            .addModifiers(Modifier.PUBLIC)
            .addParameter(type, fieldName)
            .addStatement("this.\$N = \$N", fieldName, fieldName)
            .build()
}