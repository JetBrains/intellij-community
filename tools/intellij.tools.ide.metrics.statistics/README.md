# intellij.tools.ide.metrics.statistics

Statistical machinery for comparing performance measurements against historical baselines:
a Kotlin port of Andrey Akinshin's [perfolizer](https://github.com/AndreyAkinshin/perfolizer)
(robust estimators, Mann-Whitney test, lowland multimodality detection) plus the verdict logic
that decides whether a current sample is a severe/moderate/suspicious change relative to history.

The math originates from Rider's Perforator framework
(`rider/test/framework-perforator/src/com/jetbrains/rider/test/framework/perforator/math/Perfolizer.kt`)
and is shared here so that both the platform benchmark framework
(`intellij.tools.ide.metrics.benchmark`) and Rider's Perforator can use one implementation.

**This module must stay dependency-free (kotlin-stdlib only).** Both consumers depend on it from
very different classpaths; adding platform or library dependencies here would break that reuse.

Entry point: `Perfolizer.PerformanceChecker.check(history, current)` returns a
`BenchmarkAnalysisResult` with a `BenchmarkVerdict` (direction × severity), the decision
statistics (`Collation`: Hodges-Lehmann shift and ratio, effect size, Mann-Whitney p-values),
and whether another measurement round can clarify a suspicious result.
