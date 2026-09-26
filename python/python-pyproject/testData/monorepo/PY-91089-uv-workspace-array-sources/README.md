# orgwiki

PY-91089 replica: a uv workspace whose root `pyproject.toml` declares `tool.uv.sources`
as an array of tables (`[[tool.uv.sources]]`) instead of a table. The workspace members
`orgwiki-core` and `orgwiki-test` must still be discovered as modules.
