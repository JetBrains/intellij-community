# TextMate lexer performance notes

This note records the performance work on the TextMate lexer. Each section describes one change:
what the code did, why it was slow, what the change does, and the measured effect.

## How the numbers are measured

- Benchmark: `TextMateLexerPerformanceTest` in the module `intellij.textmate.tests`.
- The run needs `-Didea.performance.tests=true`. The flag enables `@PerformanceUnitTest` classes and hides every other test.
- Every number is the median of three runs on one machine. The case noise is about 5 %.
- `testPerformanceNoCache` is the control case. It must stay within about 2 % across a comparison.
- `testPerformancePowerShell` wires the matcher as `TextMateSyntaxHighlighterFactory` wires it, so it tracks the editor.

Example command:

```bash
./community/tools/bt.cmd --module intellij.textmate.tests \
  --filter 'org.jetbrains.plugins.textmate.language.syntax.TextMateLexerPerformanceTest#testPerformancePowerShell' \
  --no-cache -- --test_arg=--jvm_flag=-Didea.performance.tests=true --test_output=all
```

## Background: IJPL-255461 (September 2026)

A JFR record of a 12 000-line PowerShell file showed that only about 60 % of the lexer time went
to the regex engine. The rest was bookkeeping in the caches around the matcher, plus objects the
lexer built and dropped for every token. Four changes removed that bookkeeping. The details and
the profile are in the first comment of
[IJPL-255461](https://youtrack.jetbrains.com/issue/IJPL-255461).

Three ideas were measured there and rejected. Do not retry them without a new profile:

- Skip the state object when a child pattern does not match. The premise was wrong: TextMate
  matches with a regex search, so most child patterns match somewhere later in the line.
- Intern the not-matched state per rule. Flat on time, and it retains one state per node.
- A same-thread Caffeine executor. Inside the noise.

## Changes

<!-- One section per commit. Filled in as the commits land. -->
