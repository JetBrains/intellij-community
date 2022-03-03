// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf.live

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.actionSystem.IdeActions
import org.jetbrains.kotlin.idea.perf.profilers.ProfilerConfig
import org.jetbrains.kotlin.idea.perf.suite.*
import org.jetbrains.kotlin.idea.perf.suite.TypePosition.AFTER_MARKER
import org.jetbrains.kotlin.idea.perf.suite.TypePosition.IN_FRONT_OF_MARKER
import org.jetbrains.kotlin.idea.perf.util.*
import org.jetbrains.kotlin.idea.testFramework.*
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction.GRADLE_PROJECT

open class PerformanceProjectsTest : AbstractPerformanceProjectsTest() {

    companion object {

        @JvmStatic
        val hwStats: Stats = Stats("helloWorld project")

        @JvmStatic
        val warmUp = WarmUpProject(hwStats)

    }

    override fun setUp() {
        super.setUp()
        warmUp.warmUp(this)
    }

    protected open fun profileConfig(): ProfilerConfig = ProfilerConfig()

    protected open fun outputConfig(): OutputConfig = OutputConfig()

    protected open fun suiteWithConfig(suiteName: String, block: PerformanceSuite.StatsScope.() -> Unit) {
        suite(
            suiteName,
            config = StatsScopeConfig(outputConfig = outputConfig(), profilerConfig = profileConfig()),
            block = block
        )
    }

    private fun PerformanceSuite.StatsScope.defaultStatsConfig() {
        with(config) {
            warmup = 8
            iterations = 15
            stabilityWatermark = 20
        }
    }

    protected fun stats(name: String) = Stats(name, profilerConfig = profileConfig(), outputConfig = outputConfig())

    protected open fun PerformanceSuite.ApplicationScope.kotlinProject(block: PerformanceSuite.ProjectScope.() -> Unit) {
        project(name = "kotlin", path = ExternalProject.KOTLIN_PROJECT_PATH, openWith = GRADLE_PROJECT, block = block)
    }

    fun testKotlinProject() {
        suiteWithConfig("kotlin project") {
            app {
                warmUpProject()

                kotlinProject {
                    defaultStatsConfig()

                    val filesToHighlight = arrayOf(
                        //"idea/idea-analysis/src/org/jetbrains/kotlin/idea/util/PsiPrecedences.kt",
                        "compiler/psi/src/org/jetbrains/kotlin/psi/KtElement.kt",
                        "compiler/psi/src/org/jetbrains/kotlin/psi/KtFile.kt",
                        "core/builtins/native/kotlin/Primitives.kt",

                        "compiler/frontend/cfg/src/org/jetbrains/kotlin/cfg/ControlFlowProcessor.kt",
                        "compiler/frontend/src/org/jetbrains/kotlin/cfg/ControlFlowInformationProvider.kt",

                        "compiler/backend/src/org/jetbrains/kotlin/codegen/state/KotlinTypeMapper.kt",
                        "compiler/backend/src/org/jetbrains/kotlin/codegen/inline/MethodInliner.kt"
                    )

                    profile(DefaultProfile)

                    filesToHighlight.forEach {fileToHighlight ->
                        fixture(fileToHighlight).use { fixture ->
                            measureHighlight(fixture)
                        }
                    }

                    profile(EmptyProfile)

                    filesToHighlight.forEach {fileToHighlight ->
                        fixture(fileToHighlight).use { fixture ->
                            measureHighlight(fixture, "empty profile")
                        }
                    }

                    fixture("compiler/psi/src/org/jetbrains/kotlin/psi/KtFile.kt").use { fixture ->
                        with(fixture.typingConfig) {
                            marker = "override fun getDeclarations(): List<KtDeclaration> {"
                            insertString = "val q = import"
                            typePosition = AFTER_MARKER
                        }

                        measureTypeAndHighlight(fixture, "typeAndHighlight in-method getDeclarations-import")

                        with(fixture.typingConfig) {
                            typePosition = IN_FRONT_OF_MARKER
                        }

                        measureTypeAndHighlight(fixture, "typeAndHighlight out-of-method import")
                    }
                }
            }
        }
    }

    fun testKotlinProjectCopyAndPaste() {
        suiteWithConfig("Kotlin copy-and-paste") {
            app {
                warmUpProject()

                kotlinProject {
                    profile(DefaultProfile)

                    defaultStatsConfig()

                    fixture("compiler/psi/src/org/jetbrains/kotlin/psi/KtFile.kt").use { originalFixture ->
                        fixture("compiler/psi/src/org/jetbrains/kotlin/psi/KtImportInfo.kt").use { targetFixture ->
                            var copied = false
                            measure<Unit>(originalFixture) {
                                before = {
                                    copied = false

                                    targetFixture.storeText()
                                    originalFixture.cursorConfig.select()
                                    targetFixture.cursorConfig.select()
                                }
                                test = {
                                    copied = originalFixture.performEditorAction(IdeActions.ACTION_COPY) &&
                                            targetFixture.performEditorAction(IdeActions.ACTION_PASTE)

                                    dispatchAllInvocationEvents()
                                }
                                after = {
                                    try {
                                        commitAllDocuments()
                                        assertTrue("copy-n-paste has not performed well", copied)
                                    } finally {
                                        targetFixture.restoreText()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun testKotlinProjectCompletionKtFile() {
        suiteWithConfig("Kotlin completion ktFile") {
            app {
                warmUpProject()

                kotlinProject {
                    profile(DefaultProfile)

                    defaultStatsConfig()

                    fixture("compiler/psi/src/org/jetbrains/kotlin/psi/KtFile.kt").use { fixture ->
                        with(fixture.typingConfig) {
                            marker = "override fun getDeclarations(): List<KtDeclaration> {"
                            typePosition = AFTER_MARKER
                            insertString = "val q = import"
                        }

                        typeAndMeasureAutoCompletion(fixture, "in-method getDeclarations-import") {
                            lookupElement = "importDirectives"
                        }

                        with(fixture.typingConfig) {
                            typePosition = IN_FRONT_OF_MARKER
                        }

                        typeAndMeasureAutoCompletion(fixture, "out-of-method import") {
                            lookupElement = "importDirectives"

                        }
                    }

                    fixture("compiler/backend/src/org/jetbrains/kotlin/codegen/state/KotlinTypeMapper.kt").use { fixture ->
                        with(fixture.typingConfig) {
                            marker = "fun mapOwner(descriptor: DeclarationDescriptor): Type {"
                            typePosition = AFTER_MARKER
                            insertString = "val b = bind"
                        }

                        typeAndMeasureAutoCompletion(fixture, "in-method completion for KotlinTypeMapper") {
                            lookupElement = "bindingContext"
                        }

                        with(fixture.typingConfig) {
                            typePosition = IN_FRONT_OF_MARKER
                        }

                        typeAndMeasureAutoCompletion(fixture, "out-of-method completion for KotlinTypeMapper") {
                            lookupElement = "bindingContext"
                        }
                    }

                    fixture("compiler/tests/org/jetbrains/kotlin/util/ArgsToParamsMatchingTest.kt").use { fixture ->
                        with(fixture.typingConfig) {
                            marker = "fun testMatchNamed() {"
                            typePosition = AFTER_MARKER
                            insertString = "testMatch"
                        }

                        typeAndMeasureAutoCompletion(fixture, "in-method completion for ArgsToParamsMatchingTest") {
                            lookupElement = "testMatchNamed"
                        }

                        with(fixture.typingConfig) {
                            typePosition = IN_FRONT_OF_MARKER
                            insertString = "fun foo() = testMatch"
                        }

                        typeAndMeasureAutoCompletion(fixture, "out-of-method completion for ArgsToParamsMatchingTest") {
                            lookupElement = "testMatchNamed"
                        }
                    }
                }
            }
        }
    }

    fun testKotlinProjectCompletionBuildGradle() {
        suiteWithConfig("kotlin completion gradle.kts") {
            app {
                warmUpProject()

                kotlinProject {
                    profile(DefaultProfile)

                    defaultStatsConfig()

                    fixture("build.gradle.kts").use { fixture ->
                        with(fixture.typingConfig) {
                            marker = "tasks {"
                            insertString = "reg"
                            typePosition = AFTER_MARKER
                        }

                        typeAndMeasureAutoCompletion(fixture, "tasks-create") {
                            lookupElement = "register"
                        }

                        with(fixture.typingConfig) {
                            marker = "tasks {"
                            insertString = "register"
                            typePosition = AFTER_MARKER
                        }

                        typeAndMeasureUndo(fixture, "type-undo")
                    }
                }
            }
        }
    }

    fun testKotlinProjectScriptDependenciesBuildGradle() {
        suiteWithConfig("kotlin scriptDependencies gradle.kts") {
            app {
                warmUpProject()

                kotlinProject {
                    profile(DefaultProfile)

                    defaultStatsConfig()

                    fixture("build.gradle.kts", updateScriptDependenciesIfNeeded = false).use { fixture ->
                        measure<Unit>(combineNameWithSimpleFileName("updateScriptDependencies", fixture)) {
                            before = {
                                fixture.openInEditor()
                            }
                            test = {
                                fixture.updateScriptDependenciesIfNeeded()
                            }
                        }
                    }
                }
            }
        }
    }

    fun testKotlinProjectBuildGradle() {
        suiteWithConfig("kotlin gradle.kts") {
            app {
                warmUpProject()

                kotlinProject {
                    profile(DefaultProfile)

                    defaultStatsConfig()

                    fixture("build.gradle.kts").use { fixture ->
                        measure<List<HighlightInfo>>(combineNameWithSimpleFileName("fileAnalysis", fixture)) {
                            before = {
                                fixture.openInEditor()
                            }
                            test = {
                                // TODO: requires some fine grain measurement till the 1st HL element
                                //  with a custom KotlinHighlightVisitor
                                fixture.doHighlighting()
                            }
                        }
                    }
                }
            }
        }
    }

}
