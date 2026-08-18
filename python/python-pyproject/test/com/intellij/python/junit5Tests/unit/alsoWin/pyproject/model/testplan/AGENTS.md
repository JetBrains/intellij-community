# pyproject testplan tests

Each test in this folder verifies the workspace model produced from a single
`testData/monorepo/<sample>` directory. One test class per sample (or grouped
under a `private const BASE` when multiple variants share a parent folder, see
`SomeProjectsWithSrcNonstandardNamingTest.kt`).

## Anatomy of a test

```kotlin
@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/<sample>")
internal class MySampleTest {
  companion object {
    // Class-level (static) project fixture: `PyDefaultTestApplication` looks one up to copy
    // the `@TestDataPath` sample into before the class runs.
    private val projectFixture = projectFixture()
  }

  private val f by pyProjectTomlSyncFixture(projectFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("root", contentRoot = ".", deps = listOf("child"), sourceRoots = listOf(".")),
      ExpectedModule("child", contentRoot = "sub" / "child", sourceRoots = listOf("sub" / "child" / "src")),
    )
  }
}
```

## Conventions

- The `companion object` holding `projectFixture` is required here: the sample is copied into
  whichever project fixture is declared as a *static* field, so an instance-level one (or none at
  all) would leave `f` looking at an empty project. Outside this folder, where there is no test
  data to copy, call `pyProjectTomlSyncFixture()` with no arguments — the sync root is then the
  project base path.
- The `$$"..."` Kotlin multi-dollar raw string on `@TestDataPath` is intentional (it keeps `$CONTENT_ROOT` literal). IDE may flag it as a
  syntax error — ignore.
- Join path segments with the `/` operator (from `...alsoWin.pyproject.div`) in
  `contentRoot` / `sourceRoots` for cross-platform path matching, e.g.
  `"sub" / "child"`. Never put a literal separator inside the string (no `"sub/child"`).
- `ExpectedModule` defaults to `type = PYPROJECT`. The implicit module created
  by `PyDefaultTestApplication` is `PYTHON` — reference it as
  `ExpectedModule(f.implicitModuleName, type = PYTHON, ...)`.
- Module names must match the `[project].name` in the sample's `pyproject.toml`.
- Module deps come from `[project].dependencies` only. Entries that appear only
  in `[tool.uv.sources]` do NOT produce a module dep — see
  `UvWorkspaceCodeInsightCheckTest.kt`.
- Known gaps are documented with `assertThrows<AssertionError>` plus a
  `PY-xxxxx` ticket reference (see `SomeProjectsWithSrcNonstandardNamingTest.kt`),
  not by deleting/weakening the assertion.

## Adding a new sample

1. Drop fixtures under `community/python/python-pyproject/testData/monorepo/<sample>/...`.
2. Create one test class per sample in this folder.
3. Name modules exactly as their `[project].name` in `pyproject.toml`.
4. Build expectations with `ExpectedModule(...)` and call
   `f.assertProjectStructure(...)` after `f.reloadProject()`.
