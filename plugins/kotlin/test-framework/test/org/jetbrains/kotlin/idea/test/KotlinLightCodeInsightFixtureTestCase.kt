// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.config.CompilerSettings.Companion.DEFAULT_ADDITIONAL_ARGUMENTS
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettings
import org.jetbrains.kotlin.idea.facet.*
import org.jetbrains.kotlin.idea.formatter.KotlinLanguageCodeStyleSettingsProvider
import org.jetbrains.kotlin.idea.formatter.KotlinStyleGuideCodeStyle
import org.jetbrains.kotlin.idea.inspections.UnusedSymbolInspection
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives.API_VERSION_DIRECTIVE
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives.COMPILER_ARGUMENTS_DIRECTIVE
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives.JVM_TARGET_DIRECTIVE
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives.LANGUAGE_VERSION_DIRECTIVE
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.KotlinRoot
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestMetadataUtil
import org.jetbrains.kotlin.test.util.slashedPath
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.rethrow
import java.io.File
import java.io.IOException
import java.nio.file.Path

abstract class KotlinLightCodeInsightFixtureTestCase : KotlinLightCodeInsightFixtureTestCaseBase() {

    private val exceptions = ArrayList<Throwable>()

    protected open val captureExceptions = false

    protected fun testDataFile(fileName: String): File = File(testDataDirectory, fileName)

    protected fun testDataFile(): File = testDataFile(fileName())

    protected fun testDataFilePath(): Path = testDataFile().toPath()

    protected fun testPath(fileName: String = fileName()): String = testDataFile(fileName).toString()

    protected fun testPath(): String = testPath(fileName())

    protected open fun fileName(): String = KotlinTestUtils.getTestDataFileName(this::class.java, this.name) ?: (getTestName(false) + ".kt")

    @Deprecated("Migrate to 'testDataDirectory'.", ReplaceWith("testDataDirectory"))
    final override fun getTestDataPath(): String = testDataDirectory.slashedPath

    open val testDataDirectory: File by lazy {
        File(TestMetadataUtil.getTestDataPath(javaClass))
    }

    override fun setUp() {
        super.setUp()
        enableKotlinOfficialCodeStyle(project)

        if (!isFirPlugin) {
            // We do it here to avoid possible initialization problems
            // UnusedSymbolInspection() calls IDEA UnusedDeclarationInspection() in static initializer,
            // which in turn registers some extensions provoking "modifications aren't allowed during highlighting"
            // when done lazily
            UnusedSymbolInspection()
        }

        VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, KotlinRoot.DIR.path)

        EditorTracker.getInstance(project)

        if (!isFirPlugin) {
            invalidateLibraryCache(project)
        }

        if (captureExceptions) {
            LoggedErrorProcessor.setNewInstance(object : LoggedErrorProcessor() {
                override fun processError(category: String, message: String?, t: Throwable?, details: Array<out String>): Boolean {
                    exceptions.addIfNotNull(t)
                    return super.processError(category, message, t, details)
                }
            })
        }
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { LoggedErrorProcessor.restoreDefaultProcessor() },
            ThrowableRunnable { disableKotlinOfficialCodeStyle(project) },
            ThrowableRunnable { super.tearDown() },
        )

        if (exceptions.isNotEmpty()) {
            exceptions.forEach { it.printStackTrace() }
            throw AssertionError("Exceptions in other threads happened")
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = getProjectDescriptorFromFileDirective()

    protected fun getProjectDescriptorFromAnnotation(): LightProjectDescriptor {
        val testMethod = this::class.java.getDeclaredMethod(name)
        return when (testMethod.getAnnotation(ProjectDescriptorKind::class.java)?.value) {
            JDK_AND_MULTIPLATFORM_STDLIB_WITH_SOURCES -> KotlinJdkAndMultiplatformStdlibDescriptor.JDK_AND_MULTIPLATFORM_STDLIB_WITH_SOURCES

            KOTLIN_JVM_WITH_STDLIB_SOURCES -> ProjectDescriptorWithStdlibSources.INSTANCE

            KOTLIN_JAVASCRIPT -> KotlinStdJSProjectDescriptor

            KOTLIN_JVM_WITH_STDLIB_SOURCES_WITH_ADDITIONAL_JS -> {
                KotlinMultiModuleProjectDescriptor(
                    KOTLIN_JVM_WITH_STDLIB_SOURCES_WITH_ADDITIONAL_JS,
                    mainModuleDescriptor = ProjectDescriptorWithStdlibSources.INSTANCE,
                    additionalModuleDescriptor = KotlinStdJSProjectDescriptor
                )
            }

            KOTLIN_JAVASCRIPT_WITH_ADDITIONAL_JVM_WITH_STDLIB -> {
                KotlinMultiModuleProjectDescriptor(
                    KOTLIN_JAVASCRIPT_WITH_ADDITIONAL_JVM_WITH_STDLIB,
                    mainModuleDescriptor = KotlinStdJSProjectDescriptor,
                    additionalModuleDescriptor = ProjectDescriptorWithStdlibSources.INSTANCE
                )
            }

            else -> throw IllegalStateException("Unknown value for project descriptor kind")
        }
    }

    protected fun getProjectDescriptorFromTestName(): LightProjectDescriptor {
        val testName = StringUtil.toLowerCase(getTestName(false))

        return when {
            testName.endsWith("runtime") -> KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
            testName.endsWith("stdlib") -> ProjectDescriptorWithStdlibSources.INSTANCE
            else -> KotlinLightProjectDescriptor.INSTANCE
        }
    }

    protected fun getProjectDescriptorFromFileDirective(): LightProjectDescriptor {
        val file = mainFile()
        if (!file.exists()) {
            return KotlinLightProjectDescriptor.INSTANCE
        }

        try {
            val fileText = FileUtil.loadFile(file, true)

            val withLibraryDirective = InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "WITH_LIBRARY:")
            val minJavaVersion = InTextDirectivesUtils.findStringWithPrefixes(fileText, "MIN_JAVA_VERSION:")?.toInt()

            if (minJavaVersion != null && !(InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME") ||
                        InTextDirectivesUtils.isDirectiveDefined(fileText, "WITH_RUNTIME"))
            ) {
                error("MIN_JAVA_VERSION so far is supported for RUNTIME/WITH_RUNTIME only")
            }
            return when {
                withLibraryDirective.isNotEmpty() ->
                    SdkAndMockLibraryProjectDescriptor(IDEA_TEST_DATA_DIR.resolve(withLibraryDirective[0]).path, true)

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_SOURCES") ->
                    ProjectDescriptorWithStdlibSources.INSTANCE

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITHOUT_SOURCES") ->
                    ProjectDescriptorWithStdlibSources.INSTANCE_NO_SOURCES

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_KOTLIN_TEST") ->
                    KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE_WITH_KOTLIN_TEST

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_FULL_JDK") ->
                    KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE_FULL_JDK

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_JDK_10") ->
                    KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance(LanguageLevel.JDK_10)

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_REFLECT") ->
                    KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE_WITH_REFLECT

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_SCRIPT_RUNTIME") ->
                    KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE_WITH_SCRIPT_RUNTIME

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME") ||
                        InTextDirectivesUtils.isDirectiveDefined(fileText, "WITH_RUNTIME") ->
                    if (minJavaVersion != null) {
                        object : KotlinWithJdkAndRuntimeLightProjectDescriptor(INSTANCE.libraryFiles, INSTANCE.librarySourceFiles) {
                            val sdkValue by lazy { sdk(minJavaVersion) }
                            override fun getSdk(): Sdk = sdkValue
                        }
                    } else {
                        KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
                    }

                InTextDirectivesUtils.isDirectiveDefined(fileText, "JS") ->
                    KotlinStdJSProjectDescriptor

                InTextDirectivesUtils.isDirectiveDefined(fileText, "ENABLE_MULTIPLATFORM") ->
                    KotlinProjectDescriptorWithFacet.KOTLIN_STABLE_WITH_MULTIPLATFORM

                else -> getDefaultProjectDescriptor()
            }
        } catch (e: IOException) {
            throw rethrow(e)
        }
    }

    protected open fun mainFile() = File(testDataDirectory, fileName())

    private fun sdk(javaVersion: Int): Sdk = when (javaVersion) {
        6 -> IdeaTestUtil.getMockJdk16()
        8 -> IdeaTestUtil.getMockJdk18()
        9 -> IdeaTestUtil.getMockJdk9()
        11 -> {
            if (SystemInfo.isJavaVersionAtLeast(javaVersion, 0, 0)) {
                PluginTestCaseBase.fullJdk()
            } else {
                error("JAVA_HOME have to point at least to JDK 11")
            }
        }

        else -> error("Unsupported JDK version $javaVersion")
    }

    protected open fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor = KotlinLightProjectDescriptor.INSTANCE

    protected fun performNotWriteEditorAction(actionId: String): Boolean {
        val dataContext = (myFixture.editor as EditorEx).dataContext

        val managerEx = ActionManagerEx.getInstanceEx()
        val action = managerEx.getAction(actionId)
        val event = AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, Presentation(), managerEx, 0)

        if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
            ActionUtil.performActionDumbAwareWithCallbacks(action, event)
            return true
        }
        return false
    }

    fun JavaCodeInsightTestFixture.configureByFile(file: File): PsiFile {
        val relativePath = file.toRelativeString(testDataDirectory)
        return configureByFile(relativePath)
    }

    fun JavaCodeInsightTestFixture.checkResultByFile(file: File) {
        val relativePath = file.toRelativeString(testDataDirectory)
        checkResultByFile(relativePath)
    }
}

object CompilerTestDirectives {
    const val LANGUAGE_VERSION_DIRECTIVE = "LANGUAGE_VERSION:"
    const val API_VERSION_DIRECTIVE = "API_VERSION:"
    const val JVM_TARGET_DIRECTIVE = "JVM_TARGET:"
    const val COMPILER_ARGUMENTS_DIRECTIVE = "COMPILER_ARGUMENTS:"

    val ALL_COMPILER_TEST_DIRECTIVES = listOf(LANGUAGE_VERSION_DIRECTIVE, JVM_TARGET_DIRECTIVE, COMPILER_ARGUMENTS_DIRECTIVE)
}

fun <T> withCustomCompilerOptions(fileText: String, project: Project, module: Module, body: () -> T): T {
    val removeFacet = !module.hasKotlinFacet()
    val configured = configureCompilerOptions(fileText, project, module)
    try {
        return body()
    } finally {
        if (configured) {
            rollbackCompilerOptions(project, module, removeFacet)
        }
    }
}

private fun configureCompilerOptions(fileText: String, project: Project, module: Module): Boolean {
    val version = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $LANGUAGE_VERSION_DIRECTIVE ")
    val apiVersion = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $API_VERSION_DIRECTIVE ")
    val jvmTarget = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $JVM_TARGET_DIRECTIVE ")
    // We can have several such directives in quickFixMultiFile tests
    // TODO: refactor such tests or add sophisticated check for the directive
    val options = InTextDirectivesUtils.findListWithPrefixes(fileText, "// $COMPILER_ARGUMENTS_DIRECTIVE ").firstOrNull()

    if (version != null || jvmTarget != null || options != null) {
        configureLanguageAndApiVersion(
            project, module,
            version ?: LanguageVersion.LATEST_STABLE.versionString,
            apiVersion
        )

        val facetSettings = KotlinFacet.get(module)!!.configuration.settings

        if (jvmTarget != null) {
            val compilerArguments = facetSettings.compilerArguments
            require(compilerArguments is K2JVMCompilerArguments) { "Attempt to specify `$JVM_TARGET_DIRECTIVE` for non-JVM test" }
            compilerArguments.jvmTarget = jvmTarget
        }

        if (options != null) {
            val compilerSettings = facetSettings.compilerSettings ?: CompilerSettings().also {
                facetSettings.compilerSettings = it
            }
            compilerSettings.additionalArguments = options
            facetSettings.updateMergedArguments()

            KotlinCompilerSettings.getInstance(project).update { this.additionalArguments = options }
        }
        return true
    }

    return false
}

fun configureRegistryAndRun(fileText: String, body: () -> Unit) {
    val registers = InTextDirectivesUtils.findListWithPrefixes(fileText, "// REGISTRY:")
        .map { it.split(' ') }
        .map { Registry.get(it.first()) to it.last() }
    try {
        for ((register, value) in registers) {
            register.setValue(value)
        }
        body()
    } finally {
        for ((register, _) in registers) {
            register.resetToDefault()
        }
    }
}

fun configureCodeStyleAndRun(
    project: Project,
    configurator: (CodeStyleSettings) -> Unit = { },
    body: () -> Unit
) {
    val testSettings = CodeStyle.createTestSettings(CodeStyle.getSettings(project))
    CodeStyle.doWithTemporarySettings(project, testSettings, Runnable {
        configurator(testSettings)
        body()
    })
}

fun enableKotlinOfficialCodeStyle(project: Project) {
    val settings = CodeStyleSettingsManager.getInstance(project).createTemporarySettings()
    KotlinStyleGuideCodeStyle.apply(settings)
    CodeStyle.setTemporarySettings(project, settings)
}

fun disableKotlinOfficialCodeStyle(project: Project) {
    CodeStyle.dropTemporarySettings(project)
}

fun resetCodeStyle(project: Project) {
    val provider = KotlinLanguageCodeStyleSettingsProvider()
    CodeStyle.getSettings(project).apply {
        removeCommonSettings(provider)
        removeCustomSettings(provider)
        clearCodeStyleSettings()
    }
}

fun runAll(
    vararg actions: ThrowableRunnable<Throwable>,
    suppressedExceptions: List<Throwable> = emptyList()
) = RunAll(actions.toList()).run(suppressedExceptions)

private fun rollbackCompilerOptions(project: Project, module: Module, removeFacet: Boolean) {
    KotlinCompilerSettings.getInstance(project).update { this.additionalArguments = DEFAULT_ADDITIONAL_ARGUMENTS }
    KotlinCommonCompilerArgumentsHolder.getInstance(project).update { this.languageVersion = LanguageVersion.LATEST_STABLE.versionString }

    if (removeFacet) {
        module.removeKotlinFacet(IdeModifiableModelsProviderImpl(project), commitModel = true)
        return
    }

    configureLanguageAndApiVersion(project, module, LanguageVersion.LATEST_STABLE.versionString, ApiVersion.LATEST_STABLE.versionString)

    val facetSettings = KotlinFacet.get(module)!!.configuration.settings
    (facetSettings.compilerArguments as? K2JVMCompilerArguments)?.jvmTarget = JvmTarget.DEFAULT.description

    val compilerSettings = facetSettings.compilerSettings ?: CompilerSettings().also {
        facetSettings.compilerSettings = it
    }
    compilerSettings.additionalArguments = DEFAULT_ADDITIONAL_ARGUMENTS
    facetSettings.updateMergedArguments()
}

fun withCustomLanguageAndApiVersion(
    project: Project,
    module: Module,
    languageVersion: String,
    apiVersion: String?,
    body: () -> Unit
) {
    val removeFacet = !module.hasKotlinFacet()
    configureLanguageAndApiVersion(project, module, languageVersion, apiVersion)
    try {
        body()
    } finally {
        if (removeFacet) {
            KotlinCommonCompilerArgumentsHolder.getInstance(project)
                .update { this.languageVersion = LanguageVersion.LATEST_STABLE.versionString }
            module.removeKotlinFacet(IdeModifiableModelsProviderImpl(project), commitModel = true)
        } else {
            configureLanguageAndApiVersion(
                project,
                module,
                LanguageVersion.LATEST_STABLE.versionString,
                ApiVersion.LATEST_STABLE.versionString
            )
        }
    }
}

private fun configureLanguageAndApiVersion(
    project: Project,
    module: Module,
    languageVersion: String,
    apiVersion: String?
) {
    WriteAction.run<Throwable> {
        val modelsProvider = IdeModifiableModelsProviderImpl(project)
        val facet = module.getOrCreateFacet(modelsProvider, useProjectSettings = false)

        val compilerArguments = facet.configuration.settings.compilerArguments
        if (compilerArguments != null) {
            compilerArguments.apiVersion = null
        }

        facet.configureFacet(languageVersion, LanguageFeature.State.DISABLED, null, modelsProvider)
        if (apiVersion != null) {
            facet.configuration.settings.apiLevel = LanguageVersion.fromVersionString(apiVersion)
        }
        KotlinCommonCompilerArgumentsHolder.getInstance(project).update { this.languageVersion = languageVersion }
        modelsProvider.commit()
    }
}

fun Project.allKotlinFiles(): List<KtFile> {
    val virtualFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, ProjectScope.getProjectScope(this))
    return virtualFiles
        .map { PsiManager.getInstance(this).findFile(it) }
        .filterIsInstance<KtFile>()
}

fun Project.allJavaFiles(): List<PsiJavaFile> {
    val virtualFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, ProjectScope.getProjectScope(this))
    return virtualFiles
        .map { PsiManager.getInstance(this).findFile(it) }
        .filterIsInstance<PsiJavaFile>()
}

fun Project.findFileWithCaret(): PsiClassOwner {
    return (allKotlinFiles() + allJavaFiles()).single {
        "<caret>" in VfsUtilCore.loadText(it.virtualFile) && !it.virtualFile.name.endsWith(".after")
    }
}

fun createTextEditorBasedDataContext(
    project: Project,
    editor: Editor,
    caret: Caret,
    additionalSteps: SimpleDataContext.Builder.() -> SimpleDataContext.Builder = { this },
): DataContext {
    val textEditorPsiDataProvider = TextEditorPsiDataProvider()
    val parentContext = DataContext { dataId -> textEditorPsiDataProvider.getData(dataId, editor, caret) }
    return SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.EDITOR, editor)
        .additionalSteps()
        .setParent(parentContext)
        .build()
}
