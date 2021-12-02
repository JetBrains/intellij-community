// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf.live

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightVisitor
import org.jetbrains.kotlin.idea.perf.profilers.ProfilerConfig
import org.jetbrains.kotlin.idea.perf.suite.*
import org.jetbrains.kotlin.idea.perf.util.*
import org.jetbrains.kotlin.idea.testFramework.Stats.Companion.TEST_KEY
import org.jetbrains.kotlin.idea.testFramework.*
import org.jetbrains.kotlin.idea.testFramework.Fixture.Companion.cleanupCaches
import org.jetbrains.kotlin.idea.testFramework.Fixture.Companion.isAKotlinScriptFile
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction.GRADLE_PROJECT
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertNotEquals

open class PerformanceProjectsTest : AbstractPerformanceProjectsTest() {

    companion object {

        @JvmStatic
        val hwStats: Stats = Stats("helloWorld project")

        @JvmStatic
        val warmUp = WarmUpProject(hwStats)

        @JvmStatic
        val timer: AtomicLong = AtomicLong()

        @JvmStatic
        val diagnosticTimer: AtomicLong = AtomicLong()

        fun resetTimestamp() {
            timer.set(0)
        }

        fun markTimestamp() {
            timer.set(System.nanoTime())
        }
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
        }
    }

    protected fun stats(name: String) = Stats(name, profilerConfig = profileConfig(), outputConfig = outputConfig())

    protected open fun PerformanceSuite.ApplicationScope.kotlinProject(block: PerformanceSuite.ProjectScope.() -> Unit) {
        project(name = "kotlin", path = ExternalProject.KOTLIN_PROJECT_PATH, openWith = GRADLE_PROJECT, block = block)
    }

    fun testHelloWorldProject() {
        suite("Hello world project") {
            myProject = perfOpenProject(stats = hwStats) {
                name("helloKotlin")

                kotlinFile("HelloMain") {
                    topFunction("main") {
                        param("args", "Array<String>")
                        body("""println("Hello World!")""")
                    }
                }

                kotlinFile("HelloMain2") {
                    topFunction("main") {
                        param("args", "Array<String>")
                        body("""println("Hello World!")""")
                    }
                }
            }

            // highlight
            perfHighlightFile("src/HelloMain.kt", hwStats)
            perfHighlightFile("src/HelloMain2.kt", hwStats)
        }
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

                    profile(EmptyProfile)

                    filesToHighlight.forEach {fileToHighlight ->
                        fixture(fileToHighlight).use { fixture ->
                            measureHighlight(fixture, "empty profile")
                        }
                    }

                    profile(DefaultProfile)

                    filesToHighlight.forEach {fileToHighlight ->
                        fixture(fileToHighlight).use { fixture ->
                            measureHighlight(fixture)
                        }
                    }

                    fixture("compiler/psi/src/org/jetbrains/kotlin/psi/KtFile.kt").use { fixture ->
                        with(fixture.typingConfig) {
                            marker = "override fun getDeclarations(): List<KtDeclaration> {"
                            insertString = "val q = import"
                            typeAfterMarker = true
                        }

                        measureTypeAndHighlight(fixture, "typeAndHighlight in-method getDeclarations-import")

                        with(fixture.typingConfig) {
                            typeAfterMarker = false
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
                                        // files could be different due to spaces
                                        //assertEquals(it.setUpValue!!.first.document.text, it.setUpValue!!.second.document.text)
                                    } finally {
                                        targetFixture.restoreText()
                                        commitAllDocuments()
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
                            insertString = "val q = import"
                            typeAfterMarker = true
                        }

                        measureTypeAndAutoCompletion(fixture, "in-method getDeclarations-import") {
                            lookupElements = listOf("importDirectives")
                        }

                        with(fixture.typingConfig) {
                            typeAfterMarker = false
                        }

                        measureTypeAndAutoCompletion(fixture, "out-of-method import") {
                            lookupElements = listOf("importDirectives")

                        }
                    }

                    fixture("compiler/backend/src/org/jetbrains/kotlin/codegen/state/KotlinTypeMapper.kt").use { fixture ->
                        with(fixture.typingConfig) {
                            marker = "fun mapOwner(descriptor: DeclarationDescriptor): Type {"
                            insertString = "val b = bind"
                            typeAfterMarker = true
                        }

                        measureTypeAndAutoCompletion(fixture, "in-method completion for KotlinTypeMapper") {
                            lookupElements = listOf("bindingContext")
                        }

                        with(fixture.typingConfig) {
                            typeAfterMarker = false
                        }

                        measureTypeAndAutoCompletion(fixture, "out-of-method completion for KotlinTypeMapper") {
                            lookupElements = listOf("bindingContext")
                        }
                    }

                    fixture("compiler/tests/org/jetbrains/kotlin/util/ArgsToParamsMatchingTest.kt").use { fixture ->
                        with(fixture.typingConfig) {
                            marker = "fun testMatchNamed() {"
                            insertString = "testMatch"
                            typeAfterMarker = true
                        }

                        measureTypeAndAutoCompletion(fixture, "in-method completion for ArgsToParamsMatchingTest") {
                            lookupElements = listOf("testMatchNamed")
                        }

                        with(fixture.typingConfig) {
                            typeAfterMarker = false
                        }

                        measureTypeAndAutoCompletion(fixture, "out-of-method completion for ArgsToParamsMatchingTest") {
                            lookupElements = listOf("testMatchNamed")
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
                            typeAfterMarker = true
                        }

                        measureTypeAndAutoCompletion(fixture, "tasks-create") {
                            lookupElements = listOf("register")
                        }

                        with(fixture.typingConfig) {
                            marker = "tasks {"
                            insertString = "register"
                            typeAfterMarker = true
                        }

                        measureTypeAndUndo(fixture, "type-undo") {}
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
                                fixture.doHighlighting()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun perfKtsFileAnalysis(
        fileName: String,
        stats: Stats,
        note: String = ""
    ) {
        val project = myProject!!
        //val disposable = Disposer.newDisposable("perfKtsFileAnalysis $fileName")

        //enableAllInspectionsCompat(project, disposable)

        replaceWithCustomHighlighter()

        project.highlightFile {
            val testName = "fileAnalysis ${notePrefix(note)}${simpleFilename(fileName)}"
            val extraStats = Stats("${stats.name} $testName")
            val extraTimingsNs = mutableListOf<Map<String, Any>?>()
            val diagnosticTimingsNs = mutableListOf<Map<String, Any>?>()

            val warmUpIterations = 30
            val iterations = 50

            performanceTest<Fixture, Pair<Long, List<HighlightInfo>>> {
                name(testName)
                stats(stats)
                warmUpIterations(warmUpIterations)
                iterations(iterations)
                setUp(perfKtsFileAnalysisSetUp(project, fileName))
                test(perfKtsFileAnalysisTest())
                tearDown(perfKtsFileAnalysisTearDown(extraTimingsNs, diagnosticTimingsNs, project))
                profilerConfig.enabled = true
            }

            val metricChildren = mutableListOf<Metric>()

            extraStats.printWarmUpTimings(
                "annotator",
                extraTimingsNs.take(warmUpIterations).toTypedArray(),
                metricChildren
            )

            extraStats.printWarmUpTimings(
                "diagnostic",
                diagnosticTimingsNs.take(warmUpIterations).toTypedArray(),
                metricChildren
            )

            extraStats.processTimings(
                "annotator",
                extraTimingsNs.drop(warmUpIterations).toTypedArray(),
                metricChildren
            )

            extraStats.processTimings(
                "diagnostic",
                diagnosticTimingsNs.drop(warmUpIterations).toTypedArray(),
                metricChildren
            )
        }
    }

    private fun replaceWithCustomHighlighter() {
        replaceWithCustomHighlighter(
            testRootDisposable,
            KotlinHighlightVisitor::class.java.name,
            TestKotlinHighlightVisitor::class.java.name
        )
    }

    fun perfKtsFileAnalysisSetUp(
        project: Project,
        fileName: String
    ): (TestData<Fixture, Pair<Long, List<HighlightInfo>>>) -> Unit {
        return {
            val fixture = Fixture.openFixture(project, fileName)

            // Note: Kotlin scripts require dependencies to be loaded
            if (isAKotlinScriptFile(fileName)) {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(fixture.psiFile)
            }

            resetTimestamp()
            it.setUpValue = fixture
        }
    }

    fun perfKtsFileAnalysisTest(): (TestData<Fixture, Pair<Long, List<HighlightInfo>>>) -> Unit {
        return {
            it.value = it.setUpValue?.let { fixture ->
                val nowNs = System.nanoTime()
                diagnosticTimer.set(-nowNs)
                Pair(nowNs, fixture.doHighlighting())
            }
        }
    }

    fun perfKtsFileAnalysisTearDown(
        extraTimingsNs: MutableList<Map<String, Any>?>,
        diagnosticTimingsMs: MutableList<Map<String, Any>?>,
        project: Project
    ): (TestData<Fixture, Pair<Long, List<HighlightInfo>>>) -> Unit {
        return {
            it.setUpValue?.let { fixture ->
                it.value?.let { v ->
                    diagnosticTimingsMs.add(mapOf(TEST_KEY to diagnosticTimer.getAndSet(0)))
                    assertTrue(v.second.isNotEmpty())
                    assertNotEquals(0, timer.get())

                    extraTimingsNs.add(mapOf(TEST_KEY to (timer.get() - v.first)))

                }
                fixture.use {
                    project.cleanupCaches()
                }
            }
        }
    }


    class TestKotlinHighlightVisitor : KotlinHighlightVisitor() {
        override fun analyze(psiFile: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
            // TODO:
            //annotationCallback {
            //    val nowNs = System.nanoTime()
            //    diagnosticTimer.addAndGet(nowNs)
            //    resetAnnotationCallback()
            //}
            try {
                return super.analyze(psiFile, updateWholeFile, holder, action)
            } finally {
                //resetAnnotationCallback()
                markTimestamp()
            }
        }

    }
}
