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

## Tests

Every code change in this directory needs a test. Write it with the `py-junit5-tests`
skill: read `.agents/skills/py-junit5-tests/SKILL.md` and follow it. It covers the JUnit 5
fixtures, the environment annotations, and the test module wiring. The skill is in the
monorepo only.

If a change cannot get a test, say so, and give the reason before you finish.
