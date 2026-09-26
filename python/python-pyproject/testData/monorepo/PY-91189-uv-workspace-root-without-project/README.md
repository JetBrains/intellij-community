# PY-91189 replica: uv workspace root without a `[project]` table

The root `pyproject.toml` declares only `[tool.uv.workspace]` and `[dependency-groups]` — there is no
`[project]` table at all. uv calls this a *virtual* project: it is a legitimate workspace root even though
it is not itself a distributable package.

The root must therefore still be parsed and become a module (named after its directory), and its
`members` must be discovered as workspace members sharing the root's environment — rather than each
member being treated as an independent project with its own venv.

Both members use the flat layout (`pkg_core/pkg_core/`, `pkg_app/pkg_app/`), so neither has a `src`
directory.
