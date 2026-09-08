package com.entitygenerator.generator

import com.entitygenerator.model.Column
import com.entitygenerator.model.ForeignKey
import com.entitygenerator.model.Table
import com.entitygenerator.model.UniqueConstraint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JpaEntityGeneratorTest {

    private val generator = JpaEntityGenerator("com.example")

    private fun column(
        name: String,
        javaType: String = "String",
        nullable: Boolean = true,
        isPrimaryKey: Boolean = false,
        length: Int? = null
    ) = Column(name = name, sqlType = javaType.uppercase(), javaType = javaType, nullable = nullable, isPrimaryKey = isPrimaryKey, length = length)

    @Test
    fun `generates a simple entity with getter setter constructors and Builder`() {
        val table = Table(
            name = "users",
            columns = listOf(
                column("id", "Long", nullable = false, isPrimaryKey = true),
                column("email", "String", nullable = false, length = 255)
            )
        )

        val files = generator.generateAll(listOf(table))

        assertEquals(1, files.size)
        val file = files.first()
        assertEquals("Users", file.className)

        val src = file.source
        assertTrue(src.contains("@Entity"))
        assertTrue(src.contains("@Table(name = \"users\")"))
        assertTrue(src.contains("public class Users {"))
        assertTrue(src.contains("@Id"))
        assertTrue(src.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertTrue(src.contains("@Column(name = \"email\", nullable = false, length = 255)"))
        assertTrue(src.contains("@Getter"))
        assertTrue(src.contains("@Setter"))
        assertTrue(src.contains("@NoArgsConstructor"))
        assertTrue(src.contains("@AllArgsConstructor"))
        assertTrue(src.contains("@Builder"))
        assertFalse(src.contains("@SuperBuilder"))
        assertFalse(src.contains("getEmail")) // no hand-written getters/setters
    }

    @Test
    fun `uses SuperBuilder and extends clause when a base class is provided`() {
        val table = Table(name = "users", columns = listOf(column("id", "Long", nullable = false, isPrimaryKey = true)))

        val files = generator.generateAll(listOf(table), baseClassName = "com.example.BaseEntity")
        val src = files.first().source

        assertTrue(src.contains("@SuperBuilder"))
        assertTrue(src.contains("import com.example.BaseEntity;"))
        assertTrue(src.contains("public class Users extends BaseEntity {"))
        assertFalse(src.contains("@Builder"))
    }

    @Test
    fun `generates an embeddable id class for composite primary keys`() {
        val table = Table(
            name = "order_items",
            columns = listOf(
                column("order_id", "Long", nullable = false, isPrimaryKey = true),
                column("product_id", "Long", nullable = false, isPrimaryKey = true),
                column("quantity", "Integer", nullable = false)
            )
        )

        val files = generator.generateAll(listOf(table))

        assertEquals(2, files.size)
        val idFile = files.first { it.className == "OrderItemsId" }
        val entityFile = files.first { it.className == "OrderItems" }

        assertTrue(idFile.source.contains("@Embeddable"))
        assertTrue(idFile.source.contains("@EqualsAndHashCode"))
        assertTrue(idFile.source.contains("private Long orderId;"))
        assertTrue(idFile.source.contains("private Long productId;"))

        assertTrue(entityFile.source.contains("@EmbeddedId"))
        assertTrue(entityFile.source.contains("private OrderItemsId id;"))
        assertTrue(entityFile.source.contains("private Integer quantity;"))
    }

    @Test
    fun `converts a foreign key into ManyToOne and OneToMany when both tables are in the batch`() {
        val customers = Table(name = "customers", columns = listOf(column("id", "Long", nullable = false, isPrimaryKey = true)))
        val orders = Table(
            name = "orders",
            columns = listOf(
                column("id", "Long", nullable = false, isPrimaryKey = true),
                column("customer_id", "Long", nullable = false)
            ),
            foreignKeys = listOf(
                ForeignKey(name = "fk_orders_customer", columns = listOf("customer_id"), referencedTable = "customers", referencedColumns = listOf("id"))
            )
        )

        val files = generator.generateAll(listOf(customers, orders))

        val ordersFile = files.first { it.className == "Orders" }
        assertTrue(ordersFile.source.contains("@ManyToOne"))
        assertTrue(ordersFile.source.contains("@JoinColumn(name = \"customer_id\", nullable = false)"))
        assertTrue(ordersFile.source.contains("private Customers customer;"))
        assertFalse(ordersFile.source.contains("private Long customerId;")) // replaced by the relationship, not duplicated

        val customersFile = files.first { it.className == "Customers" }
        assertTrue(customersFile.source.contains("@OneToMany(mappedBy = \"customer\")"))
        assertTrue(customersFile.source.contains("private List<Orders> orders;")) // catches the pluralize bug if it regresses
    }

    @Test
    fun `falls back to a plain scalar column when the referenced table is not in the batch`() {
        val orders = Table(
            name = "orders",
            columns = listOf(
                column("id", "Long", nullable = false, isPrimaryKey = true),
                column("customer_id", "Long", nullable = false)
            ),
            foreignKeys = listOf(
                ForeignKey(name = "fk_orders_customer", columns = listOf("customer_id"), referencedTable = "customers", referencedColumns = listOf("id"))
            )
        )

        val files = generator.generateAll(listOf(orders))
        val src = files.first().source

        assertFalse(src.contains("@ManyToOne"))
        assertTrue(src.contains("private Long customerId;"))
    }

    @Test
    fun `reflects unique constraints and schema on the Table annotation`() {
        val table = Table(
            name = "accounts",
            schema = "auth",
            columns = listOf(column("id", "Long", nullable = false, isPrimaryKey = true), column("email", "String")),
            uniqueConstraints = listOf(UniqueConstraint(name = "uk_email", columns = listOf("email")))
        )

        val src = generator.generateAll(listOf(table)).first().source

        assertTrue(src.contains("schema = \"auth\""))
        assertTrue(src.contains("uniqueConstraints = {@UniqueConstraint(columnNames = {\"email\"})}"))
    }

    @Test
    fun `applies a name override only when generating a single table`() {
        val table = Table(name = "users", columns = listOf(column("id", "Long", nullable = false, isPrimaryKey = true)))

        val files = generator.generateAll(listOf(table), nameOverride = "Account")

        assertEquals("Account", files.first().className)
        assertTrue(files.first().source.contains("@Table(name = \"users\")")) // real table name preserved, not the override
    }
}