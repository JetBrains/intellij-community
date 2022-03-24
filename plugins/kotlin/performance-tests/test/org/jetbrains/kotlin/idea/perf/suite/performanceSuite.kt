// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf.suite

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.*
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.usages.Usage
import com.intellij.util.ArrayUtilRt
import com.intellij.util.containers.toArray
import com.intellij.util.indexing.UnindexedFilesUpdater
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.testFramework.ProjectBuilder
import org.jetbrains.kotlin.idea.perf.util.ExternalProject
import org.jetbrains.kotlin.idea.perf.util.ProfileTools.Companion.disableAllInspections
import org.jetbrains.kotlin.idea.perf.util.ProfileTools.Companion.enableAllInspections
import org.jetbrains.kotlin.idea.perf.util.ProfileTools.Companion.enableInspections
import org.jetbrains.kotlin.idea.perf.util.ProfileTools.Companion.enableSingleInspection
import org.jetbrains.kotlin.idea.perf.util.ProfileTools.Companion.initDefaultProfile
import org.jetbrains.kotlin.idea.perf.util.TeamCity
import org.jetbrains.kotlin.idea.perf.util.logMessage
import org.jetbrains.kotlin.idea.test.GradleProcessOutputInterceptor
import org.jetbrains.kotlin.idea.test.invalidateLibraryCache
import org.jetbrains.kotlin.idea.testFramework.*
import org.jetbrains.kotlin.idea.testFramework.Fixture.Companion.cleanupCaches
import org.jetbrains.kotlin.idea.util.getProjectJdkTableSafe
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

class PerformanceSuite {
    companion object {
        fun suite(
            name: String,
            stats: StatsScope,
            block: (StatsScope) -> Unit
        ) {
            TeamCity.suite(name) {
                stats.stats.use {
                    block(stats)
                }
            }
        }

        private fun PsiFile.highlightFile(toIgnore: IntArray = ArrayUtilRt.EMPTY_INT_ARRAY): List<HighlightInfo> {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)!!
            val editor = EditorFactory.getInstance().getEditors(document).first()
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            return CodeInsightTestFixtureImpl.instantiateAndRun(this, editor, toIgnore, true)
        }

        fun rollbackChanges(vararg file: VirtualFile) {
            val fileDocumentManager = FileDocumentManager.getInstance()
            runInEdtAndWait {
                fileDocumentManager.reloadFiles(*file)
            }

            ProjectManagerEx.getInstanceEx().openProjects.forEach { project ->
                val psiDocumentManagerBase = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase

                runInEdtAndWait {
                    psiDocumentManagerBase.clearUncommittedDocuments()
                    psiDocumentManagerBase.commitAllDocuments()
                }
            }
        }
    }

    class StatsScope(val config: StatsScopeConfig, val stats: Stats, val rootDisposable: Disposable) {
        fun app(f: ApplicationScope.() -> Unit) = ApplicationScope(rootDisposable, this).use(f)

        fun <T> measure(name: String, f: MeasurementScope<T>.() -> Unit): List<T?> =
            MeasurementScope<T>(name, stats, config).apply(f).run()

        fun <T> measure(name: String, f: MeasurementScope<T>.() -> Unit, after: (() -> Unit)?): List<T?> =
            MeasurementScope<T>(name, stats, config, after = after).apply(f).run()

        fun typeAndMeasureAutoCompletion(name: String, fixture: Fixture, f: TypeAndAutoCompletionMeasurementScope.() -> Unit, after: (() -> Unit)?): List<String?> =
            TypeAndAutoCompletionMeasurementScope(fixture, typeTestPrefix = "typeAndAutocomplete", name = name, stats = stats, config = config, after = after).apply(f).run()

        fun typeAndMeasureUndo(name: String, fixture: Fixture, f: TypeAndUndoMeasurementScope.() -> Unit, after: (() -> Unit)?): List<String?> =
            TypeAndUndoMeasurementScope(fixture, typeTestPrefix = "typeAndUndo", name = name, stats = stats, config = config, after = after).apply(f).run()

        fun measureTypeAndHighlight(name: String, fixture: Fixture, f: TypeAndHighlightMeasurementScope.() -> Unit, after: (() -> Unit)?): List<HighlightInfo?> =
            TypeAndHighlightMeasurementScope(fixture, typeTestPrefix = "", name = name, stats = stats, config = config, after = after).apply(f).run()

        fun logStatValue(name: String, value: Any) {
            logMessage { "buildStatisticValue key='${stats.name}: $name' value='$value'" }
            TeamCity.statValue("${stats.name}: $name", value)
        }
    }

    class ApplicationScope(val rootDisposable: Disposable, val stats: StatsScope) : AutoCloseable {
        val application = initApp(rootDisposable)
        val jdk: Sdk = initSdk(rootDisposable)

        fun project(externalProject: ExternalProject, refresh: Boolean = false, block: ProjectScope.() -> Unit) =
            ProjectScope(ProjectScopeConfig(externalProject, refresh), this).use(block)

        fun project(block: ProjectWithDescriptorScope.() -> Unit) =
            ProjectWithDescriptorScope(this).use(block)

        fun project(name: String? = null, path: String, openWith: ProjectOpenAction = ProjectOpenAction.EXISTING_IDEA_PROJECT, block: ProjectScope.() -> Unit) =
            ProjectScope(ProjectScopeConfig(path, openWith, name = name), this).use(block)

        fun gradleProject(name: String? = null, path: String, refresh: Boolean = false, block: ProjectScope.() -> Unit) =
            ProjectScope(ProjectScopeConfig(path, ProjectOpenAction.GRADLE_PROJECT, refresh, name = name), this).use(block)

        fun warmUpProject() = project {
            descriptor {
                name("helloWorld")

                module {
                    kotlinStandardLibrary()

                    kotlinFile("HelloMain") {
                        topFunction("main") {
                            param("args", "Array<String>")
                            body("""println("Hello World!")""")
                        }
                    }
                }
            }

            fixture("src/HelloMain.kt").use { fixture ->
                fixture.highlight().firstOrNull { it.severity == HighlightSeverity.WARNING }
                    ?: error("`[UNUSED_PARAMETER] Parameter 'args' is never used` has to be highlighted")
            }
        }

        override fun close() {
            application.setDataProvider(null)
        }

        companion object {
            fun initApp(rootDisposable: Disposable): TestApplicationManager {
                val application = TestApplicationManager.getInstance()
                GradleProcessOutputInterceptor.install(rootDisposable)
                return application
            }

            fun initSdk(rootDisposable: Disposable): Sdk {
                return runWriteAction {
                    val jdkTableImpl = JavaAwareProjectJdkTableImpl.getInstanceEx()
                    val homePath = if (jdkTableImpl.internalJdk.homeDirectory!!.name == "jre") {
                        jdkTableImpl.internalJdk.homeDirectory!!.parent.path
                    } else {
                        jdkTableImpl.internalJdk.homePath!!
                    }

                    val roots = mutableListOf<String>()
                    roots += homePath
                    System.getenv("JDK_18")?.let {
                        roots += it
                    }
                    VfsRootAccess.allowRootAccess(rootDisposable, *roots.toTypedArray())

                    val javaSdk = JavaSdk.getInstance()
                    val jdk = javaSdk.createJdk("1.8", homePath)
                    val internal = javaSdk.createJdk("IDEA jdk", homePath)
                    val gradle = javaSdk.createJdk(GRADLE_JDK_NAME, homePath)

                    val jdkTable = getProjectJdkTableSafe()
                    jdkTable.addJdk(jdk, rootDisposable)
                    jdkTable.addJdk(internal, rootDisposable)
                    jdkTable.addJdk(gradle, rootDisposable)
                    KotlinSdkType.setUpIfNeeded()
                    jdk
                }
            }
        }
    }

    abstract class AbstractProjectScope(val app: ApplicationScope) : AutoCloseable {
        abstract val project: Project
        private val openFiles = mutableListOf<VirtualFile>()
        private var compilerTester: CompilerTester? = null

        fun profile(profile: ProjectProfile): Unit = when (profile) {
            EmptyProfile -> project.disableAllInspections()
            DefaultProfile -> project.initDefaultProfile()
            FullProfile -> project.enableAllInspections()
            is CustomProfile -> project.enableInspections(*profile.inspectionNames.toArray(emptyArray()))
        }

        fun withCompiler() {
            compilerTester = CompilerTester(project, ModuleManager.getInstance(project).modules.toList(), null)
        }

        fun rebuildProject() {
            compilerTester?.rebuild() ?: error("compiler isn't ready for compilation")
        }

        fun Fixture.highlight() = highlight(psiFile)

        fun highlight(editorFile: PsiFile?, toIgnore: IntArray = ArrayUtilRt.EMPTY_INT_ARRAY) =
            editorFile?.highlightFile(toIgnore) ?: error("editor isn't ready for highlight")

        fun findUsages(config: CursorConfig): Set<Usage> {
            val offset = config.fixture.editor.caretModel.offset
            val psiFile = config.fixture.psiFile
            val psiElement = psiFile.findElementAt(offset) ?: error("psi element not found at ${psiFile.virtualFile} : $offset")
            val ktDeclaration = PsiTreeUtil.getParentOfType(psiElement, KtDeclaration::class.java)
                ?: error("KtDeclaration not found at ${psiFile.virtualFile} : $offset")
            return config.fixture.findUsages(ktDeclaration)
        }

        fun enableSingleInspection(inspectionName: String) =
            this.project.enableSingleInspection(inspectionName)

        fun enableAllInspections() =
            this.project.enableAllInspections()

        fun editor(path: String) =
            Fixture.openInEditor(project, path).psiFile.also { openFiles.add(it.virtualFile) }

        fun fixture(path: String, updateScriptDependenciesIfNeeded: Boolean = true): Fixture {
            return fixture(Fixture.projectFileByName(project, path).virtualFile, path, updateScriptDependenciesIfNeeded)
        }

        fun fixture(file: VirtualFile, fileName: String? = null, updateScriptDependenciesIfNeeded: Boolean = true): Fixture {
            val fixture = Fixture.openFixture(project, file, fileName)
            openFiles.add(fixture.vFile)
            if (file.name.endsWith(KotlinFileType.EXTENSION)) {
                assert(fixture.psiFile is KtFile) {
                    "$file expected to be a Kotlin file"
                }
            }
            if (updateScriptDependenciesIfNeeded) {
                fixture.updateScriptDependenciesIfNeeded()
            }
            return fixture
        }

        fun <T> measure(vararg name: String, clearCaches: Boolean = true, f: MeasurementScope<T>.() -> Unit): List<T?> {
            val after = wrapAfter(clearCaches)
            return app.stats.measure(name.joinToString("-"), f, after)
        }

        private fun wrapAfter(clearCaches: Boolean): () -> Unit {
            val after = if (clearCaches) {
                fun() { project.cleanupCaches() }
            } else {
                fun() {}
            }
            return after
        }

        fun <T> measure(fixture: Fixture, f: MeasurementScope<T>.() -> Unit): List<T?> =
            measure(fixture.fileName, f = f)

        fun <T> measure(fixture: Fixture, vararg name: String, f: MeasurementScope<T>.() -> Unit): List<T?> =
            measure(combineName(fixture, *name), f = f)

        fun measureTypeAndHighlight(
            fixture: Fixture,
            vararg name: String,
            f: TypeAndHighlightMeasurementScope.() -> Unit = {}
        ): List<HighlightInfo?> {
            val after = wrapAfter(true)
            return app.stats.measureTypeAndHighlight(combineName(fixture, *name), fixture, f, after)
        }

        fun measureHighlight(fixture: Fixture, vararg name: String): List<List<HighlightInfo>?> {
            return measure(combineNameWithSimpleFileName("highlighting", fixture, *name)) {
                before = {
                    fixture.openInEditor()
                }
                test = {
                    fixture.highlight()
                }
                after = {
                    fixture.close()
                    project.cleanupCaches()
                }
            }
        }

        fun typeAndMeasureAutoCompletion(fixture: Fixture, vararg name: String, clearCaches: Boolean = true, f: TypeAndAutoCompletionMeasurementScope.() -> Unit): List<String?> {
            val after = wrapAfter(clearCaches)
            return app.stats.typeAndMeasureAutoCompletion(combineName(fixture, *name), fixture, f, after)
        }

        fun typeAndMeasureUndo(fixture: Fixture, vararg name: String, clearCaches: Boolean = true, f: TypeAndUndoMeasurementScope.() -> Unit = {}): List<String?> {
            val after = wrapAfter(clearCaches)
            return app.stats.typeAndMeasureUndo(combineName(fixture, *name), fixture, f, after)
        }

        fun combineName(fixture: Fixture, vararg name: String) =
            listOf(name.joinToString("-"), fixture.fileName)
                .filter { it.isNotEmpty() }
                .joinToString(" ")

        fun combineNameWithSimpleFileName(type: String, fixture: Fixture, vararg name: String): String =
            listOf(type, name.joinToString("-"), fixture.simpleFilename())
                .filter { it.isNotEmpty() }
                .joinToString(" ")

        override fun close(): Unit = RunAll(
            { compilerTester?.tearDown() },
            { project.let { prj -> app.application.closeProject(prj) } }
        ).run()
    }

    class ProjectWithDescriptorScope(app: ApplicationScope) : AbstractProjectScope(app) {
        private var descriptor: ProjectBuilder? = null
        override val project: Project by lazy {
            val builder = descriptor ?: error("project is not configured")
            val openProject = builder.openProjectOperation()

            openProject.openProject().also {
                openProject.postOpenProject(it)
            }
        }

        fun descriptor(descriptor: ProjectBuilder.() -> Unit) {
            this.descriptor = ProjectBuilder().apply(descriptor)
        }

    }

    class ProjectScope(config: ProjectScopeConfig, app: ApplicationScope) : AbstractProjectScope(app) {
        override val project: Project = initProject(config, app)

        companion object {
            fun initProject(config: ProjectScopeConfig, app: ApplicationScope): Project {
                val projectPath = File(config.path).canonicalPath

                UsefulTestCase.assertTrue("path ${config.path} does not exist, check README.md", File(projectPath).exists())

                val openProject = OpenProject(
                    projectPath = projectPath,
                    projectName = config.projectName,
                    jdk = app.jdk,
                    projectOpenAction = config.openWith
                )
                val project = ProjectOpenAction.openProject(openProject)
                openProject.projectOpenAction.postOpenProject(project, openProject)

                // indexing
                if (config.refresh) {
                    invalidateLibraryCache(project)
                }

                CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project)

                dispatchAllInvocationEvents()
                with(DumbService.getInstance(project)) {
                    queueTask(UnindexedFilesUpdater(project))
                    completeJustSubmittedTasks()
                }
                dispatchAllInvocationEvents()

                Fixture.enableAnnotatorsAndLoadDefinitions(project)

                app.application.setDataProvider(TestDataProvider(project))

                return project
            }
        }
    }
}

sealed class ProjectProfile
object EmptyProfile : ProjectProfile()
object DefaultProfile : ProjectProfile()
object FullProfile : ProjectProfile()
data class CustomProfile(val inspectionNames: List<String>) : ProjectProfile()

fun UsefulTestCase.suite(
    suiteName: String? = null,
    config: StatsScopeConfig = StatsScopeConfig(),
    block: PerformanceSuite.StatsScope.() -> Unit
) {
    val stats = Stats(config.name ?: suiteName ?: name, outputConfig = config.outputConfig, profilerConfig = config.profilerConfig)
    PerformanceSuite.suite(
        suiteName ?: this.javaClass.name,
        PerformanceSuite.StatsScope(config, stats, testRootDisposable),
        block
    )
}
