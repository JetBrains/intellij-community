# glob-members

PY-89677 replica (follow-up comment): a uv workspace whose root `pyproject.toml` has no
`[project]` section (a non-package root, `[tool.uv] package = false`) and declares its
members with glob patterns (`apps/*`, `packages/*`) plus root-only `[tool.uv.sources]`.

The app member `apps/app` depends on `packages/package`
(`dependencies = ["package"]`); PyCharm must discover both members as modules and set up
the `app` → `package` dependency so that `import package` resolves.
