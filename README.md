# entity-generator

An IntelliJ IDEA plugin that generates JPA-annotated Java entity classes directly from SQL DDL — paste a `CREATE TABLE` statement, point at a `.sql` file, or connect straight to a live database, and get ready-to-use Lombok-powered entity classes in seconds.

## Features

- **Three ways to generate entities**, all from a right-click on any folder in the Project view under **Generate Entity**:
    - **Paste DDL** — paste one or more `CREATE TABLE` statements directly into a text box
    - **From .sql File** — pick an existing `.sql` file from disk
    - **From DB Source** — connect to a live database and pick tables from a searchable, schema-grouped list
- **Composite primary keys** are generated as a proper `@Embeddable`/`@EmbeddedId` pair, not flattened into a single field
- **Foreign keys** become real `@ManyToOne`/`@OneToMany` relationships when both sides of the relationship are generated together in the same batch, falling back to a plain scalar column when the referenced table isn't part of the batch
- **Unique constraints** are reflected as `@Table(uniqueConstraints = ...)`
- **Schema-aware** — `@Table(schema = "...")` is set automatically when reading from a live database or from schema-qualified DDL
- **Lombok-powered output** — `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, and `@Builder` (or `@SuperBuilder` when extending a base class) instead of hand-written boilerplate
- **Optional base class support** — extend a shared base entity (e.g. for audit fields) by supplying a class name at generation time
- **Persistent, reusable DB connections** — saved connections (host/port/database/type/SSL) persist across IDE restarts, with passwords stored securely via the IDE's OS-level credential store, never in plain text
- **Multi-database support** — PostgreSQL, MySQL, and H2 out of the box

## Free vs. Pro

Currently, every feature described above — including database connections — is fully free. Database-source generation is planned to become a **Pro** (paid) feature once the project has enough traction to justify JetBrains Marketplace's freemium review process. Paste DDL and .sql File generation will always remain free.

## Requirements

- IntelliJ IDEA (Community or Ultimate) 2023.1+
- The generated entities use `jakarta.persistence` and Lombok annotations — **the target project you generate into must have Lombok on its classpath with annotation processing enabled** (`org.projectlombok:lombok` as a `compileOnly` dependency + annotation processor). The plugin itself does not add this for you.

## Usage

1. Right-click any folder in the Project view.
2. Choose **Generate Entity**, then one of:
    - **Paste DDL...** — paste your `CREATE TABLE` statement(s), optionally give the entity a custom class name (only applies when generating a single table), optionally specify a base class to extend, and click OK.
    - **From .sql File...** — pick a `.sql` file containing one or more `CREATE TABLE` statements.
    - **From DB Source...** — pick a saved connection (or create a new one), select one or more tables from the schema-grouped, searchable list, and generate.
3. Generated `.java` files are written directly into the folder you right-clicked, using its package.

### Example

Given:

```sql
CREATE TABLE customers (
    id BIGINT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    total DECIMAL(10,2),
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);
```

The plugin generates a `Customers.java` with a `@OneToMany(mappedBy = "customer") private List<Orders> orders;`, and an `Orders.java` with `@ManyToOne @JoinColumn(name = "customer_id", nullable = false) private Customer customer;` — a real relationship, not two disconnected classes.

## Building from source

```
gradlew build
```

To launch a sandbox IDE with the plugin installed for manual testing:

```
gradlew runIde
```

## Known limitations

- Foreign key columns that are *also* part of a composite primary key (the classic many-to-many join-table pattern) are currently left as plain scalar fields inside the `@EmbeddedId` class rather than using Lombok/JPA's `@MapsId` — full support for that pattern is planned.
- `@SuperBuilder` requires the base class you extend to *also* be annotated with `@SuperBuilder` — this is a Lombok requirement, not something the plugin can work around.
- DDL-based unique constraint detection depends on JSqlParser's parsing of `UNIQUE (...)` syntax, which has some known gaps with certain vendor-specific clauses. The DB-source path (reading live `DatabaseMetaData`) doesn't have this limitation, since it doesn't depend on parsing SQL text at all.
- Pluralization for generated collection field names (e.g. `List<Order> orders`) uses a simple heuristic and may not be correct for irregular plurals.

## Roadmap

- `@MapsId` support for join-table-style composite foreign keys
- Additional database support beyond PostgreSQL/MySQL/H2

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](./LICENSE) for details.

## Contributing

Issues and pull requests are welcome at [github.com/Mohamed-Fameen/entity-generator](https://github.com/Mohamed-Fameen/entity-generator).