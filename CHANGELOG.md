# Changelog

## [Unreleased]

## [1.0.1] - 2026-09-12

### Added

- Generate JPA entity classes from pasted DDL, a .sql file, or a live database connection
- Composite primary key support via `@EmbeddedId`
- Foreign keys converted to `@ManyToOne`/`@OneToMany` relationships when both tables are generated together
- Unique constraints reflected as `@Table(uniqueConstraints = ...)`
- Schema-aware `@Table(schema = ...)` generation
- Lombok-powered output (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`/`@SuperBuilder`)
- Optional base class support for generated entities
- Persistent, reusable database connections with credentials stored via the IDE's secure credential store
- Support for PostgreSQL, MySQL, and H2