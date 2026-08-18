# PY-91195-uv-path-dependency-marker-array

A byte-for-byte copy of `uv_path_dependencies` with exactly one change: `subuv2`'s
`[tool.uv.sources]` entry for `subuv1` is an **array of tables** (several sources selected by
platform marker — valid uv syntax) instead of the single inline table `{ path = "../subuv1" }`.

Nothing here declares `[tool.uv.workspace]`, and that is the point: outside a workspace,
`UvPyProjectManager#getProjectStructure` skips the project entirely, so the `subuv2 -> subuv1`
edge can only come from the generic reader `getToolSpecificDependenciesFromTomlTable`, which
still assumes a single inline table. Inside a workspace the already-fixed uv resolver would
contribute the same edge and mask the gap.
