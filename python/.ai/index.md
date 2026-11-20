# `community/python/` — module instructions

This directory is shared by multiple teams (**PyCharm Codeinsight**, **PyCharm
Exec**, **Jupyter** / Data Science). Apply only the section matching the
code you are touching.

## PyCharm Codeinsight

If you are doing **code-insight work** — regarding typing/syntax/tool integration/editing — read and follow
[`../code-insight-conventions.md`](../code-insight-conventions.md) before edits or
reviews.

## PyCharm Exec

If you are doing **exec work** — regarding SDK/interpreters/packaging/run/debug/etc — read and follow
[`../exec-conventions.md`](../exec-conventions.md) before edits or reviews.

## Never use `Sdk`. Use `PythonInterpreter`

This rule is for all teams and all code in this directory, in production code and in tests.

Do not use `com.intellij.openapi.projectRoots.Sdk` to represent a Python interpreter. Use
`com.intellij.python.sdk.backend.PythonInterpreter`. Do not use `Sdk` as a parameter, a property, a return type or
a local value. `Sdk` is a legacy API, and it will be removed from PyCharm.

* If platform code gives you an `Sdk`, convert it immediately with `Sdk.pythonInterpreterResult`. Then use only the
  `PythonInterpreter`.
* `PythonInterpreter` is a sealed class. To check the kind (`NativePythonInterpreter` or `TargetPythonInterpreter`),
  use an exhaustive `when`.
* If you change code that takes an `Sdk`, change it to take a `PythonInterpreter`.
* Only the module `intellij.python.sdk.backend` can use `Sdk`.

The project inspection "Use PythonInterpreter instead of Sdk in PyCharm" (suppress ID `PySdkUsage`) finds each use
of `Sdk`. Do not suppress it in new code.

Before you plan, edit or review code that reads, sets, stores or tests a Python interpreter, read
[`python-sdk/AGENTS.md`](../python-sdk/AGENTS.md). The guide explains the interpreter library flow: how a module
names its interpreter, where the facts live, and which API to use.

## Read a Caffeine `AsyncCache` with `getOrLoad`

This rule is for all teams and all code in this directory.

To get a value from a Caffeine `AsyncCache` in a coroutine, use
`com.intellij.python.pytools.backend.getOrLoad`:

```kotlin
cache.getOrLoad(key, scope) { key -> load(key) }
```

Do not write `cache.get(key) { _, _ -> scope.future { ... } }.await()`. All callers of one key share one future.
`CompletionStage.await()` cancels that future when its caller is cancelled, so one cancelled caller stops the load
for all other callers. `getOrLoad` waits through `asDeferred()`, which does not cancel the future.

## Tests

Every code change in this directory needs a test. Write it with the `pycharm-dev-workflow`
skill: read `.agents/skills/pycharm-dev-workflow/junit5-tests/index.md` and follow it. It covers the JUnit 5
fixtures, the environment annotations, and the test module wiring. The skill is in the
monorepo only.

If a change cannot get a test, say so, and give the reason before you finish.

## Django

If you edit Django support — in `plugins/django-core/` or in `python/python-django/` — read and follow
[`django-multimodule.md`](../../../python/.ai/django-multimodule.md). It holds the module-context rules for a
multimodule project.
