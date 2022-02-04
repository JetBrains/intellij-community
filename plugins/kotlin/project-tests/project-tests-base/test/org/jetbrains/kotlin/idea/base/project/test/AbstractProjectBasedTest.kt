// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.project.test

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.KotlinPluginKind
import org.jetbrains.kotlin.idea.base.assertKotlinPluginKind
import org.jetbrains.kotlin.idea.perf.*
import org.jetbrains.kotlin.idea.perf.common.HighlightFile
import org.jetbrains.kotlin.idea.perf.common.ProjectAction
import org.jetbrains.kotlin.idea.perf.common.ProjectData
import org.jetbrains.kotlin.idea.perf.common.TypeAndAutocompleteInFile
import org.jetbrains.kotlin.idea.perf.live.AbstractPerformanceProjectsTest
import org.jetbrains.kotlin.idea.perf.util.Benchmark
import org.jetbrains.kotlin.idea.perf.util.BenchmarkAdditionalData
import org.jetbrains.kotlin.idea.perf.util.EsUploaderConfiguration
import org.jetbrains.kotlin.idea.perf.util.TeamCity
import org.jetbrains.kotlin.idea.testFramework.EditorFile
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction

abstract class AbstractProjectBasedTest : AbstractPerformanceProjectsTest() {
    abstract val pluginKind: KotlinPluginKind

    abstract val isBenchmark: Boolean

    private val frontendName: String
        get() = when (pluginKind) {
            KotlinPluginKind.FE10_PLUGIN -> "FE10"
            KotlinPluginKind.FIR_PLUGIN -> "FIR"
        }

    private val testSuitePrefix get() = frontendName

    override fun setUp() {
        super.setUp()
        assertKotlinPluginKind(pluginKind)

        if (isBenchmark) {
            val hwStats = Stats("$testSuitePrefix warmup project")
            val warmUpProject = WarmUpProject(hwStats)
            warmUpProject.warmUp(this)
        }
    }

    protected abstract fun invalidateCaches(project: Project)

    protected fun test(
        project: ProjectData,
        actions: List<ProjectAction>,
        profile: ProjectBasedTestPreferences,
    ) {
        val suiteName = "$testSuitePrefix ${project.name}"
        TeamCity.suite(suiteName) {
            Stats(
                name = suiteName,
                esUploaderConfiguration = if (isBenchmark) esUploaderConfiguration else EsUploaderConfiguration.DoNotUploadToEs,
            ).use { stats ->
                perfOpenRustPluginProject(project, stats)
                for (action in actions) {
                    runAction(action, stats, project, profile)
                }
            }
        }
    }

    private fun patchBenchmark(benchmark: Benchmark, project: ProjectData, file: String, actionId: String): Benchmark =
        benchmark.copy(
            additionalData = ProjectPerfTestAdditionalData(
                frontend = frontendName,
                project = project.name,
                file = file,
                action = actionId,
            )
        )


    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun runAction(action: ProjectAction, stats: Stats, project: ProjectData, profile: ProjectBasedTestPreferences) {
        val projectTestOverride = settingsOverride<Any, Any> {
            warmUpIterations(profile.warmUpIterations)
            iterations(profile.iterations)
            reportCompilerCounters(false)
        }
        val benchmarkSettingOverride = settingsOverride<Any, Any> {
            benchmarkTransformer { benchmark ->
                patchBenchmark(benchmark, project, action.filePath, action.id)
            }
        }
        when (action) {
            is HighlightFile -> {
                perfHighlightFileEmptyProfile(
                    action.filePath,
                    stats = stats,
                    stopAtException = true,
                    tearDown = { invalidateCaches(project()) },
                    overrides = buildList<PerfTestSettingsOverride<EditorFile, List<HighlightInfo>>> {
                        add(projectTestOverride as PerfTestSettingsOverride<EditorFile, List<HighlightInfo>>)
                        if (profile.checkForValidity) {
                            add(settingsOverride {
                                afterTestCheck { (file, highlightings) ->
                                    val psiFile = file?.psiFile ?: error("No file was provided")
                                    checkHighlightings(psiFile, highlightings)
                                }
                            })
                        }
                        add(benchmarkSettingOverride as PerfTestSettingsOverride<EditorFile, List<HighlightInfo>>)
                    }
                )
            }
            is TypeAndAutocompleteInFile -> {
                perfTypeAndAutocomplete(
                    stats,
                    fileName = action.filePath,
                    marker = action.typeAfter,
                    insertString = action.textToType,
                    highlightFileBeforeStartTyping = true,
                    lookupElements = action.expectedLookupElements,
                    note = action.note ?: "",
                    stopAtException = true,
                    overrides = listOf(
                        projectTestOverride as PerfTestSettingsOverride<Unit, Array<LookupElement>>,
                        benchmarkSettingOverride as PerfTestSettingsOverride<Unit, Array<LookupElement>>,
                    )
                )
            }
        }
    }

    private fun checkHighlightings(psiFile: PsiFile, highlightings: List<HighlightInfo>?): TestCheckResult {
        val filename = psiFile.name
        if (highlightings == null) return TestCheckResult.Failure("Highlighting of $filename did not complete successfully")
        val errors = highlightings.filter { it.severity == HighlightSeverity.ERROR }
        if (errors.isEmpty()) {
            return TestCheckResult.Success
        } else {
            return TestCheckResult.Failure(
                "The following errors arose during highlighting of $filename:\n${errors.joinToString(separator = "\n")}"
            )
        }
    }

    private fun perfOpenRustPluginProject(projectData: ProjectData, stats: Stats) {
        withGradleNativeSetToFalse(projectData.openAction) {
            myProject = perfOpenProject(
                name = projectData.name,
                stats = stats,
                note = "",
                path = projectData.path.toAbsolutePath().toString(),
                openAction = projectData.openAction,
                fast = true
            )
        }
    }

    private inline fun withGradleNativeSetToFalse(openAction: ProjectOpenAction, body: () -> Unit) {
        when (openAction) {
            ProjectOpenAction.GRADLE_PROJECT -> {
                val oldValue = System.getProperty(ORG_GRADLE_NATIVE)
                System.setProperty("org.gradle.native", "false")
                try {
                    body()
                } finally {
                    when (oldValue) {
                        null -> System.clearProperty(ORG_GRADLE_NATIVE)
                        else -> System.setProperty(ORG_GRADLE_NATIVE, oldValue)
                    }
                }
            }
            else -> {
                body()
            }
        }
    }

    companion object {
        private const val ORG_GRADLE_NATIVE = "org.gradle.native"

         val esUploaderConfiguration = EsUploaderConfiguration.UploadToEs(
            indexName = "kotlin_fir_ide_benchmarks",
        )
    }
}

data class ProjectPerfTestAdditionalData(
    var frontend: String,
    var project: String,
    var file: String,
    var action: String,
) : BenchmarkAdditionalData {
    override fun getId(): String {
        return "${frontend}_${project}_${file}_$action".filter { it in 'a'..'z' || it in 'A'..'Z' || it == '_' }
    }
}

class ProjectBasedTestPreferences(
    val warmUpIterations: Int,
    val iterations: Int,
    val checkForValidity: Boolean,
)