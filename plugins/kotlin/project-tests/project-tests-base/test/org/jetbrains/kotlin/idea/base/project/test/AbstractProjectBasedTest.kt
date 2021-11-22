// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.project.test

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.perf.*
import org.jetbrains.kotlin.idea.perf.common.HighlightFile
import org.jetbrains.kotlin.idea.perf.common.ProjectAction
import org.jetbrains.kotlin.idea.perf.common.ProjectData
import org.jetbrains.kotlin.idea.perf.common.TypeAndAutocompleteInFile
import org.jetbrains.kotlin.idea.perf.live.AbstractPerformanceProjectsTest
import org.jetbrains.kotlin.idea.perf.util.TeamCity
import org.jetbrains.kotlin.idea.testFramework.EditorFile
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction

abstract class AbstractProjectBasedTest : AbstractPerformanceProjectsTest() {
    abstract val testPrefix: String

    abstract val warmUpOnHelloWorldProject: Boolean

    override fun setUp() {
        super.setUp()
        if (warmUpOnHelloWorldProject) {
            val hwStats = Stats("$testPrefix warmup project")
            val warmUpProject = WarmUpProject(hwStats)
            warmUpProject.warmUp(this)
        }
    }

    protected abstract fun invalidateCaches(project: Project)

    protected fun test(
        name: String,
        project: ProjectData,
        actions: List<ProjectAction>,
        profile: ProjectBasedTestPreferences,
    ) {
        TeamCity.suite("$testPrefix $name") {
            Stats("$testPrefix $name").use { stats ->
                perfOpenRustPluginProject(project, stats)
                for (action in actions) {
                    runAction(action, stats, profile)
                }
            }
        }
    }


    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun runAction(action: ProjectAction, stats: Stats, profile: ProjectBasedTestPreferences) {
        val projectTestOverride = settingsOverride<Any, Any> {
            warmUpIterations(profile.warmUpIterations)
            iterations(profile.iterations)
            reportCompilerCounters(false)
        }
        when (action) {
            is HighlightFile -> {
                perfHighlightFileEmptyProfile(
                    action.filePath,
                    stats = stats,
                    stopAtException = true,
                    tearDown = { invalidateCaches(project()) },
                    overrides = buildList {
                        add(projectTestOverride as PerfTestSettingsOverride<EditorFile, List<HighlightInfo>>)
                        if (profile.checkForValidity) {
                            add(settingsOverride<EditorFile, List<HighlightInfo>> {
                                afterTestCheck { (file, highlightings) ->
                                    val psiFile = file?.psiFile ?: error("No file was provided")
                                    checkHighlightings(psiFile, highlightings)
                                }
                            })
                        }
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
                        projectTestOverride as PerfTestSettingsOverride<Unit, Array<LookupElement>>
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
                "The following errors arose during highlighting of $filename:${errors.joinToString(separator = "\n")}"
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
    }
}

class ProjectBasedTestPreferences(
    val warmUpIterations: Int,
    val iterations: Int,
    val checkForValidity: Boolean,
)