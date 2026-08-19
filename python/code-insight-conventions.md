# PyCharm Codeinsight — Conventions

Conventions for work in code-insight modules.

## New files: Kotlin

- Write all new classes / files in Kotlin. Editing existing Java in place is fine.

## General code style guidelines

- Strive to write self-explanatory code. Don't add comments unless they explain something 
  non-obvious from the code.
- In general, don't put issue numbers for the tickets currently being worked on in code comments. 
  Keeping them in commit messages and identifying the corresponding issues with `git blame` is 
  enough. Referring to other related tickets, e.g., to followup tasks in TODO comments or reported 
  corner cases and blockers justifying a non-obvious workaround is perfectly fine, though.
- Familiarize yourself with the Python-specific helper APIs, primarily `PyUtil` and `PyPsiUtils`.
- When doing PSI tree traversal, use the standard extension methods on `PsiElement` defined in
  `psiTreeUtil.kt`, as well as static helper methods from `PsiTreeUtil`. In some cases
  `SyntaxTraverser` can also come handy.

## Types

### Prefer `PyAnyType` over raw `null`

- Use `PyAnyType.unknown` / `PyAnyType.any` to represent `typing.Any` (or
  `Unknown`) `PyType` value instead of raw `null`.
- This is registry-gated and forward-looking: new code should not introduce
  `null` to mean "Any".

### Distinguish class objects from instances

- A `PyClassType` can describe either an instance of the class *or* the class
  object itself (the definition). Use `PyInstantiableType.isDefinition()` to tell
  them apart before treating one as the other.

## Evaluating type-system behaviour

When reasoning about how a type *should* be inferred or what counts as an error,
cross-checking against the real third-party type checkers is a fast way to gain
insight (and to see where they disagree — they often differ on strictness, what
is flagged, and message wording).

Use the **`compare-python-typecheckers`** skill: it runs a file or snippet
through `ty`, `pyrefly`, `basedpyright`, `mypy`, and `zuban` (via `uvx`, no
install) and collates the results into one report.

```bash
uv run <skill-dir>/scripts/compare_typecheckers.py -c 'x: int = "a"'
uv run <skill-dir>/scripts/compare_typecheckers.py test.py --tools ty,mypy
```

To run one checker by hand: `uvx ty check test.py`, `uvx pyrefly check test.py`,
`uvx basedpyright test.py` (file is positional), `uvx mypy test.py`,
`uvx zuban check test.py`.

## Tests

### use `PyCodeInsightTestCase`

- Write new inspection / code-insight tests against `PyCodeInsightTestCase`
  (Kotlin, JUnit5, inline assertion mini-language) — not the older
  `PyInspectionTestCase` / `PyTestCase`.
- It is new and may not yet cover every scenario. If it lacks a capability your
  test needs, **enhance `PyCodeInsightTestCase` itself** rather than falling
  back to an older base class.

### code-style

- tests should use the latest language level and syntax features by default,
  only using the older form when that is the explicit purpose of the test. e.g.:
  ```kotlin
  @Test
  fun `type variable inference`() = test("""
    def f[T](t: T) -> T: ...
  
    result = f(1)
    # └ TYPE int
    """)
  
  @Test
  // this is needed due to special handling in the implementation, not because every test requires an old version
  fun `type variable inference old style`() = test("""
    from typing import TypeVar
  
    T = TypeVar("T")
  
    def f(t: T) -> T: ...
  
    result = f(1)
    # └ TYPE int
    """)
  ```

### annotate with `@TestFor`

- Annotate new tests and test classes with `@TestFor`, binding each to what it
  covers — never a bare `// PY-XXXXX` comment.
- Bind to the YouTrack issue with `issues`:
  `@TestFor(issues = ["PY-XXXXX"])`.
- Bind to the production class(es) under test with `classes`:
  `@TestFor(classes = [PySomething::class])` (multiple allowed:
  `@TestFor(classes = [PyFoo::class, PyBar::class])`).
- Both may be combined when a test covers an issue against a specific class:
  `@TestFor(issues = ["PY-XXXXX"], classes = [PySomething::class])`.

### Running tests

- Code-insight tests live in module **`intellij.python.community.tests`**
  (sources under `community/python/testSrc/`).
  Run them with `./tests.cmd --module intellij.python.community.tests --test <pattern>`.
- **`<pattern>` must be a full FQN, a wildcard (`*MyTest`), or `Class#method`.** A bare
  class name (`PyVersionSpecifiersTest`) or the `Class.method` form matches **nothing** —
  and a zero-match run still prints "tests passed" and exits 0. So a green result with a
  bare class name means *nothing ran*, not that it passed. Always pass the FQN (e.g.
  `com.intellij.python.junit5Tests.unit.PyVersionSpecifiersTest`) and confirm the run
  reports a non-zero test count.

### Python 2 and Python 3 in tests

- All new test scenarios should be checked against the latest supported version of Python 3 unless
  the test is testing the boundary of a feature: that a new feature is not supported in an older
  version of the language, or a deprecated feature is no longer supported in a newer version.
- When selecting a suitable test class for new tests, start with checking newer code 
  insight tests, inheriting from `PyCodeInsightTestCase` (many are listed in
  `PyPureTypingTestSuite`). Update the legacy tests, inheriting from `PyTestCase`, only if the
  changed functionality is something not covered by `PyCodeInsightTestCase`, e.g. formatting,
  refactorings, editing actions, etc. or there are many related test cases already present in one
  of the legacy tests that is yet to be ported.
- Some legacy test classes have separate Python 2 and Python 3 variants, e.g.
  `PyArgumentListInspectionTest` for Python 2 scenarios and `Py3ArgumentListInspectionTest` for
  Python 3 scenarios. The main difference is that non-"Py3" versions of tests use the Python 2
  version of the standard library in the bundled copy of Typeshed. 
  When extending a legacy test, use the "Py3" version of a test class whenever it's available.

### Update the standard test suites

- Add all new tests to the relevant standard test suites, in particular `PythonAllTestsSuite` for
  all tests and `PyPureTypingTestSuite` and `PythonAllTypesTestSuite` for tests involving type 
  inference and type checking.

### Add multifile tests for changes involving PSI stubs

- For changes that involve PSI stubs and having different analysis paths for elements with available
  complete AST vs. only PSI stub trees (often controlled with `TypeEvalContext.maySwitchToAST`,
  `StubAwareComputation` or `StubBasedPsiElement.getStub`) add test scenarios with
  multiple Python files and imported names to make sure that analysis results in both cases match.
