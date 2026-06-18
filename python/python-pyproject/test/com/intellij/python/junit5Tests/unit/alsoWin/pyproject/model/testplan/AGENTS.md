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
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  }
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("root", contentRoot = ".", deps = listOf("child"), sourceRoots = listOf(".")),
      ExpectedModule("child", contentRoot = "sub${SEP}child", sourceRoots = listOf("sub${SEP}child${SEP}src")),
    )
  }
}
```

## Conventions

- The `$$"..."` Kotlin multi-dollar raw string on `@TestDataPath` is intentional
  (it keeps `$CONTENT_ROOT` literal). IDE may flag it as a syntax error — ignore.
- Use `${SEP}` (from `...alsoWin.pyproject.SEP`) in `contentRoot` / `sourceRoots`
  for cross-platform path matching. Never hardcode `/` or `\`.
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
