# orgwiki

PY-91089 replica: a uv workspace whose root `pyproject.toml` declares `tool.uv.workspace`
as an array of tables (`[[tool.uv.workspace]]`) instead of a table. The members
`orgwiki-core` and `orgwiki-test` must still be discovered as modules.
