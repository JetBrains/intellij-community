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

### The line hash is computed once per line (October 2026)

**What the code did.** The match-rule cache keys a lookup by the rule, the offset, the scope, and
the line (`TextMateMatchRuleCacheKey` in `TextMateCachingSyntaxMatcherCore.kt`). Its `hashCode`
called `TextMateStringImpl.hashCode`, which recomputed `bytes.contentHashCode()` on every call.

**Why it was slow.** The lexer calls `matchRule` once per step of `parseLine`, so a line of N
bytes with M steps hashed N×M bytes. The cost is quadratic in the line length. IJPL-255461 named
this cost as the next place to look.

**The change.** `TextMateStringImpl` computes the content hash on the first `hashCode` call and
keeps it, with the 0-as-absent convention of `String.hashCode`. The bytes are immutable, so the
hash cannot go stale. The first, eager variant hashed every string at creation. It was discarded:
a capture rule re-encodes a line prefix into a new string for each capture, and most of those
strings never reach a cache key.

**Measured effect** (best attempt.min of 3 runs / median of the 3 run medians):

| Case | Before | After |
|---|---|---|
| `testPerformanceLongLines` | 645 / 690 ms | 486 / 515 ms (−25 %) |
| `testPerformancePowerShell` | 1073 / 1386 ms | 1206 / 1402 ms (noise) |
| `testPerformance` | 285 / 424 ms | 289 / 317 ms (noise) |
| `testPerformanceNoCache` (control) | 9791 / 14473 ms | 6273 / 12061 ms (noise) |

The gain shows only on the long-line case, as the quadratic model predicts. The average line of
`EditorImpl.java` is short, so the other cases stay inside the noise.
