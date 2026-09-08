package com.entitygenerator.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DdlParserTest {

    private val parser = DdlParser()

    @Test
    fun `parses a simple table with a single primary key`() {
        val ddl = """
            CREATE TABLE users (
                id BIGINT PRIMARY KEY,
                email VARCHAR(255) NOT NULL,
                full_name VARCHAR(100)
            );
        """.trimIndent()

        val tables = parser.parse(ddl)

        assertEquals(1, tables.size)
        val table = tables.first()
        assertEquals("users", table.name)
        assertEquals(3, table.columns.size)

        val id = table.columns.first { it.name == "id" }
        assertTrue(id.isPrimaryKey)
        assertFalse(id.nullable)

        val email = table.columns.first { it.name == "email" }
        assertFalse(email.nullable)
        assertEquals("String", email.javaType)

        val fullName = table.columns.first { it.name == "full_name" }
        assertTrue(fullName.nullable)
    }

    @Test
    fun `detects composite primary key from table-level constraint`() {
        val ddl = """
            CREATE TABLE order_items (
                order_id BIGINT,
                product_id BIGINT,
                quantity INT NOT NULL,
                PRIMARY KEY (order_id, product_id)
            );
        """.trimIndent()

        val table = parser.parse(ddl).first()

        assertEquals(2, table.primaryKeyColumns.size)
        assertTrue(table.primaryKeyColumns.any { it.name == "order_id" })
        assertTrue(table.primaryKeyColumns.any { it.name == "product_id" })
    }

    @Test
    fun `detects foreign key constraint`() {
        val ddl = """
            CREATE TABLE customers (
                id BIGINT PRIMARY KEY
            );

            CREATE TABLE orders (
                id BIGINT PRIMARY KEY,
                customer_id BIGINT NOT NULL,
                FOREIGN KEY (customer_id) REFERENCES customers(id)
            );
        """.trimIndent()

        val tables = parser.parse(ddl)
        val orders = tables.first { it.name == "orders" }

        assertEquals(1, orders.foreignKeys.size)
        val fk = orders.foreignKeys.first()
        assertEquals(listOf("customer_id"), fk.columns)
        assertEquals("customers", fk.referencedTable)
        assertEquals(listOf("id"), fk.referencedColumns)
    }

    @Test
    fun `detects table-level and inline unique constraints`() {
        val ddl = """
            CREATE TABLE accounts (
                id BIGINT PRIMARY KEY,
                email VARCHAR(255) UNIQUE,
                username VARCHAR(50) NOT NULL,
                UNIQUE (username)
            );
        """.trimIndent()

        val table = parser.parse(ddl).first()

        assertEquals(2, table.uniqueConstraints.size)
        assertTrue(table.uniqueConstraints.any { it.columns == listOf("email") })
        assertTrue(table.uniqueConstraints.any { it.columns == listOf("username") })
    }

    @Test
    fun `parses multiple CREATE TABLE statements in one DDL block`() {
        val ddl = """
            CREATE TABLE a (id BIGINT PRIMARY KEY);
            CREATE TABLE b (id BIGINT PRIMARY KEY);
        """.trimIndent()

        val tables = parser.parse(ddl)

        assertEquals(2, tables.size)
        assertEquals(setOf("a", "b"), tables.map { it.name }.toSet())
    }

    @Test
    fun `maps common SQL types to expected Java types`() {
        val ddl = """
            CREATE TABLE types_test (
                a VARCHAR(10),
                b BIGINT,
                c INT,
                d BOOLEAN,
                e DECIMAL(10,2),
                f DATE,
                g TIMESTAMP
            );
        """.trimIndent()

        val table = parser.parse(ddl).first()
        val typeByName = table.columns.associate { it.name to it.javaType }

        assertEquals("String", typeByName["a"])
        assertEquals("Long", typeByName["b"])
        assertEquals("Integer", typeByName["c"])
        assertEquals("Boolean", typeByName["d"])
        assertEquals("BigDecimal", typeByName["e"])
        assertEquals("LocalDate", typeByName["f"])
        assertEquals("LocalDateTime", typeByName["g"])
    }

    @Test
    fun `returns empty list for invalid or non-CREATE-TABLE input`() {
        val tables = parser.parse("SELECT * FROM foo;")
        assertTrue(tables.isEmpty())
    }
}