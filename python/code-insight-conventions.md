# PyCharm Codeinsight — Conventions

Conventions for work in code-insight modules.

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
  }
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

## New files: Kotlin

- Write all new classes / files in Kotlin. Editing existing Java in place is
  fine.
