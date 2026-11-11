# Tools

> **Legend.** `*` after a field name marks a property that is required by the JSON schema. For input parameters this means the caller must pass the value. For output fields it means the value is always present within its enclosing object. Nested `*` is scoped to the parent object's presence — required nested fields are only guaranteed when the optional parent object is emitted.

## Refactoring

- [rename_refactoring](tools/rename_refactoring.md) — Rename a symbol and every reference to it. Set preview=true to analyze the rename and write nothing.

