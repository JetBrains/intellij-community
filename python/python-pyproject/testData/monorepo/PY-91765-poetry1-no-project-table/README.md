# PY-91765 replica: Poetry-1 subprojects without a `[project]` table

Poetry 1.x predates PEP 621, so a project it generated declares its metadata in `[tool.poetry]` and has
no `[project]` table at all. Both subprojects here are pure Poetry-1: `name`, `version`, `authors` (the
Poetry-1 `"Name <mail>"` string form) and `packages` live under `[tool.poetry]`, dependencies live in
`[tool.poetry.dependencies]` (a table keyed by package name, not a PEP 621 array), and `build-system`
pins `poetry-core` 1.x.

Such a `pyproject.toml` must still be recognized as a project, taking its name from
`[tool.poetry].name`, so that both directories become pyproject modules with their `src` source root and
`poetry1app`'s `[tool.poetry.dependencies]` path entry becomes a module dependency on `poetry1lib`.
Without the `[tool.poetry]` fallback neither `pyproject.toml` yields a project, so no module is detected
at all and the whole tree stays under the single implicit root module — the symptom reported in the
ticket.
