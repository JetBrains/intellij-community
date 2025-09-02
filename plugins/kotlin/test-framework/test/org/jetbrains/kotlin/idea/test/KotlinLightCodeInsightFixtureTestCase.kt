// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
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
import com.intellij.testFramework.*
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.CompilerSettings.Companion.DEFAULT_ADDITIONAL_ARGUMENTS
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.facet.hasKotlinFacet
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.fe10.highlighting.suspender.KotlinHighlightingSuspender
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts.coroutineContext
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts.kotlinxCoroutines
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.configureFacet
import org.jetbrains.kotlin.idea.facet.getOrCreateFacet
import org.jetbrains.kotlin.idea.facet.removeKotlinFacet
import org.jetbrains.kotlin.idea.formatter.KotlinLanguageCodeStyleSettingsProvider
import org.jetbrains.kotlin.idea.formatter.KotlinOfficialStyleGuide
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.inspections.UnusedSymbolInspection
import org.jetbrains.kotlin.idea.serialization.updateCompilerArguments
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives.API_VERSION_DIRECTIVE
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives.COMPILER_ARGUMENTS_DIRECTIVE
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives.COMPILER_PLUGIN_OPTIONS
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives.JVM_TARGET_DIRECTIVE
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives.KOTLIN_COMPILER_VERSION_DIRECTIVE
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives.LANGUAGE_VERSION_DIRECTIVE
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives.PROJECT_LANGUAGE_VERSION_DIRECTIVE
import org.jetbrains.kotlin.idea.test.util.slashedPath
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.rethrow
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.test.assertEquals

abstract class KotlinLightCodeInsightFixtureTestCase : KotlinLightCodeInsightFixtureTestCaseBase() {

    private val exceptions = ArrayList<Throwable>()
    private var mockLibraryFacility: MockLibraryFacility? = null

    protected open val captureExceptions = false

    protected fun dataFile(fileName: String): File = File(testDataDirectory, fileName)

    protected fun dataFile(): File = dataFile(fileName())

    protected fun dataFilePath(): Path = dataFile().toPath()

    protected fun dataFilePath(fileName: String = fileName()): String = dataFile(fileName).toString()

    protected open fun fileName(): String = KotlinTestUtils.getTestDataFileName(this::class.java, this.name) ?: (getTestName(false) + ".kt")

    @Deprecated("Migrate to 'testDataDirectory'.", ReplaceWith("testDataDirectory"))
    final override fun getTestDataPath(): String = testDataDirectory.slashedPath

    open val testDataDirectory: File by lazy {
        File(TestMetadataUtil.getTestDataPath(javaClass))
    }

    override fun setUp() {
        super.setUp()
        enableKotlinOfficialCodeStyle(project)

        if (pluginMode == KotlinPluginMode.K1) {
            // We do it here to avoid possible initialization problems
            // UnusedSymbolInspection() calls IDEA UnusedDeclarationInspection() in static initializer,
            // which in turn registers some extensions provoking "modifications aren't allowed during highlighting"
            // when done lazily
            UnusedSymbolInspection()
        }

        VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, KotlinRoot.DIR.path)

        EditorTracker.getInstance(project)

        invalidateLibraryCache(project)
        mockLibraryFacility = loadMockLibrary()?.also {
            it.setUp(module)
        }
    }

    private fun loadMockLibrary(): MockLibraryFacility? {
        val file = mainFile()
        if (!file.exists() || !file.isFile) {
            return null
        }
        val fileText = FileUtil.loadFile(file, true)
        val withLibraryDirective = InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "WITH_LIBRARY:")

        when (withLibraryDirective.size) {
            0 -> return null
            1 -> {
                val libraryDir = file.parentFile!!.resolve(withLibraryDirective.single())
                return MockLibraryFacility(
                    source = libraryDir,
                    options = parseExtraLibraryCompileOptions(libraryDir),
                )
            }
            else -> error("Only one library directive is allowed")
        }
    }

    private fun parseExtraLibraryCompileOptions(libraryDir: File): List<String> {
        val extraDirectivesFile = libraryDir.resolve("directives.test")
        if (extraDirectivesFile.exists()) {
            val extraDirectivesFileText = FileUtil.loadFile(extraDirectivesFile, true)
            val extraCompilerArguments =
                InTextDirectivesUtils.findStringWithPrefixes(extraDirectivesFileText, COMPILER_ARGUMENTS_DIRECTIVE)
            if (extraCompilerArguments != null) {
                return extraCompilerArguments.split(" ")
            }
        }
        return emptyList()
    }

    override fun runBare(testRunnable: ThrowableRunnable<Throwable>) {
        if (captureExceptions) {
            LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
                override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
                    exceptions.addIfNotNull(t)
                    return super.processError(category, message, details, t)
                }
            }) {
                super.runBare(testRunnable)
            }
        }
        else {
            super.runBare(testRunnable)
        }
    }

    override fun tearDown() {
        runAll(
            { mockLibraryFacility?.tearDown(module) },
            { KotlinSdkType.removeKotlinSdkInTests() },
            { runCatching { project }.getOrNull()?.let { disableKotlinOfficialCodeStyle(it) } },
            { super.tearDown() },
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

            KOTLIN_JVM_WITH_STDLIB_SOURCES -> ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

            KOTLIN_JAVASCRIPT -> KotlinStdJSProjectDescriptor

            KOTLIN_JVM_WITH_STDLIB_SOURCES_WITH_ADDITIONAL_JS -> {
                KotlinMultiModuleProjectDescriptor(
                    KOTLIN_JVM_WITH_STDLIB_SOURCES_WITH_ADDITIONAL_JS,
                    mainModuleDescriptor = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources(),
                    additionalModuleDescriptor = KotlinStdJSProjectDescriptor
                )
            }

            KOTLIN_JAVASCRIPT_WITH_ADDITIONAL_JVM_WITH_STDLIB -> {
                KotlinMultiModuleProjectDescriptor(
                    KOTLIN_JAVASCRIPT_WITH_ADDITIONAL_JVM_WITH_STDLIB,
                    mainModuleDescriptor = KotlinStdJSProjectDescriptor,
                    additionalModuleDescriptor = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()
                )
            }

            else -> throw IllegalStateException("Unknown value for project descriptor kind")
        }
    }

    protected fun getProjectDescriptorFromTestName(): LightProjectDescriptor {
        val testName = StringUtil.toLowerCase(getTestName(false))

        return when {
            testName.endsWith("runtime") -> KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
            testName.endsWith("stdlib") -> ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()
            else -> getDefaultProjectDescriptor()
        }
    }

    protected fun getProjectDescriptorFromFileDirective(): LightProjectDescriptor {
        val file = mainFile()
        if (!file.exists()) {
            return KotlinLightProjectDescriptor.INSTANCE
        }

        try {
            val fileText = FileUtil.loadFile(file, true)

            val minJavaVersion = InTextDirectivesUtils.findStringWithPrefixes(fileText, "MIN_JAVA_VERSION:")?.toInt()

            if (minJavaVersion != null && !(InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME") ||
                        InTextDirectivesUtils.isDirectiveDefined(fileText, "WITH_STDLIB"))
            ) {
                error("MIN_JAVA_VERSION so far is supported for RUNTIME/WITH_STDLIB only")
            }
            return when {
                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_SOURCES") ->
                    ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITHOUT_SOURCES") ->
                    ProjectDescriptorWithStdlibSources.getInstanceNoSources()

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_KOTLIN_TEST") ->
                    KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithKotlinTest()

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_FULL_JDK") ->
                    KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_JDK_10") ->
                    KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithStdlibJdk10()

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_REFLECT") ->
                    KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithReflect()

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_SCRIPT_RUNTIME") ->
                    KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithScriptRuntime()

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_STDLIB_JDK8") ->
                    KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithStdlibJdk8()

                InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME") ||
                        InTextDirectivesUtils.isDirectiveDefined(fileText, "WITH_STDLIB") -> {
                    val instance = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// WITH_STDLIB ")
                        ?.let { version -> KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance(version) }
                        ?: KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
                    if (minJavaVersion != null) {
                        object : KotlinWithJdkAndRuntimeLightProjectDescriptor(
                            instance.libraryFiles,
                            instance.librarySourceFiles,
                            LanguageLevel.parse(minJavaVersion.toString())!!,
                        ) {
                            val sdkValue by lazy { sdk(minJavaVersion) }
                            override fun getSdk(): Sdk = sdkValue
                        }
                    } else {
                        instance
                    }
                }

                InTextDirectivesUtils.isDirectiveDefined(fileText, "JS_WITH_STDLIB") ->
                    KotlinStdJSWithStdLibProjectDescriptor

                InTextDirectivesUtils.isDirectiveDefined(fileText, "JS_WITH_DOM_API_COMPAT") ->
                    KotlinStdJSWithDomApiCompatProjectDescriptor

                InTextDirectivesUtils.isDirectiveDefined(fileText, "JS") ->
                    KotlinStdJSProjectDescriptor

                InTextDirectivesUtils.isDirectiveDefined(fileText, "ENABLE_MULTIPLATFORM") ->
                    KotlinProjectDescriptorWithFacet.KOTLIN_STABLE_WITH_MULTIPLATFORM

                InTextDirectivesUtils.isDirectiveDefined(fileText, "WITH_COROUTINES") ->
                    KotlinWithJdkAndRuntimeLightProjectDescriptor(listOf(kotlinxCoroutines, coroutineContext), emptyList())

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
        11, 17 -> {
            if (Runtime.version().feature() >= javaVersion) {
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
        ActionUtil.updateAction(action, event)
        if (event.presentation.isEnabled) {
            ActionUtil.performAction(action, event)
            return true
        }
        return false
    }

    fun JavaCodeInsightTestFixture.configureByFile(file: File): PsiFile {
        val relativePath = file.toRelativeString(testDataDirectory)
        return configureByFile(relativePath)
    }

    fun JavaCodeInsightTestFixture.configureByFiles(vararg file: File): List<PsiFile> {
        val relativePaths = file.map { it.toRelativeString(testDataDirectory) }.toTypedArray()
        return configureByFiles(*relativePaths).toList()
    }

    fun JavaCodeInsightTestFixture.checkResultByFile(file: File) {
        val relativePath = file.toRelativeString(testDataDirectory)
        checkResultByFile(relativePath)
    }
}

object CompilerTestDirectives {
    const val KOTLIN_COMPILER_VERSION_DIRECTIVE = "KOTLIN_COMPILER_VERSION:"
    const val LANGUAGE_VERSION_DIRECTIVE = "LANGUAGE_VERSION:"
    const val PROJECT_LANGUAGE_VERSION_DIRECTIVE = "PROJECT_LANGUAGE_VERSION:"
    const val API_VERSION_DIRECTIVE = "API_VERSION:"
    const val JVM_TARGET_DIRECTIVE = "JVM_TARGET:"
    const val COMPILER_ARGUMENTS_DIRECTIVE = "COMPILER_ARGUMENTS:"
    const val COMPILER_PLUGIN_OPTIONS = "COMPILER_PLUGIN_OPTIONS:"


    val ALL_COMPILER_TEST_DIRECTIVES = listOf(
        LANGUAGE_VERSION_DIRECTIVE,
        PROJECT_LANGUAGE_VERSION_DIRECTIVE,
        JVM_TARGET_DIRECTIVE,
        COMPILER_ARGUMENTS_DIRECTIVE,
    )
}

fun <T> withCustomCompilerOptions(fileText: String, project: Project, module: Module, body: () -> T): T {
    val removeFacet = !module.hasKotlinFacet()
    val configured = runInEdtAndGet { configureCompilerOptions(fileText, project, module) }
    try {
        return body()
    } finally {
        if (configured) {
            runInEdtAndWait { rollbackCompilerOptions(project, module, removeFacet) }
        }
    }
}

private fun configureCompilerOptions(fileText: String, project: Project, module: Module): Boolean {
    val jvmTarget = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $JVM_TARGET_DIRECTIVE ")
    // We can have several such directives in quickFixMultiFile tests
    // TODO: refactor such tests or add sophisticated check for the directive
    val options = InTextDirectivesUtils.findListWithPrefixes(fileText, "// $COMPILER_ARGUMENTS_DIRECTIVE ").firstOrNull()

    val compilerVersion = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $KOTLIN_COMPILER_VERSION_DIRECTIVE ")
        ?.let(IdeKotlinVersion.Companion::opt)
    val languageVersion = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $LANGUAGE_VERSION_DIRECTIVE ")
        ?.let { LanguageVersion.fromVersionString(it) }
    val projectLanguageVersion = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $PROJECT_LANGUAGE_VERSION_DIRECTIVE ")
        ?.let { LanguageVersion.fromVersionString(it) }
    val apiVersion = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $API_VERSION_DIRECTIVE ")
        ?.let { ApiVersion.parse(it) }

    InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $COMPILER_PLUGIN_OPTIONS ")
        ?.split("\\s+,\\s+")?.toTypedArray()?.let {
            KotlinCommonCompilerArgumentsHolder.getInstance(project).update { this.pluginOptions = it }
        }

    if (compilerVersion != null || languageVersion != null || apiVersion != null || jvmTarget != null || options != null ||
        projectLanguageVersion != null
    ) {
        configureLanguageAndApiVersion(
            project,
            module,
            compilerVersion
                ?: languageVersion?.let(IdeKotlinVersion.Companion::fromLanguageVersion)
                ?: KotlinPluginLayout.standaloneCompilerVersion,
            languageVersion,
            projectLanguageVersion,
            apiVersion,
        )

        val facetSettings = KotlinFacet.get(module)!!.configuration.settings

        if (jvmTarget != null) {
            facetSettings.updateCompilerArguments {
                require(this is K2JVMCompilerArguments) { "Attempt to specify `$JVM_TARGET_DIRECTIVE` for non-JVM test" }
                this.jvmTarget = jvmTarget
            }
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

fun configureRegistryAndRun(project: Project, fileText: String, body: () -> Unit) {
    KotlinHighlightingSuspender.getInstance(project) // register Registry listener, otherwise registry changes wouldn't be picked up by ElementAnnotator
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
    KotlinOfficialStyleGuide.apply(settings)
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

    val bundledKotlinVersion = KotlinPluginLayout.standaloneCompilerVersion

    KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
        this.languageVersion = bundledKotlinVersion.languageVersion.versionString
    }

    if (removeFacet) {
        module.removeKotlinFacet(ProjectDataManager.getInstance().createModifiableModelsProvider(project), commitModel = true)
        return
    }

    configureLanguageAndApiVersion(project, module, bundledKotlinVersion)

    val facetSettings = KotlinFacet.get(module)!!.configuration.settings
    facetSettings.updateCompilerArguments {
        (this as? K2JVMCompilerArguments)?.jvmTarget = JvmTarget.DEFAULT.description
    }

    val compilerSettings = facetSettings.compilerSettings ?: CompilerSettings().also {
        facetSettings.compilerSettings = it
    }
    compilerSettings.additionalArguments = DEFAULT_ADDITIONAL_ARGUMENTS
    facetSettings.updateMergedArguments()
}

fun withCustomLanguageAndApiVersion(
    project: Project,
    module: Module,
    languageVersion: LanguageVersion,
    apiVersion: ApiVersion?,
    body: () -> Unit
) {
    val removeFacet = !module.hasKotlinFacet()
    configureLanguageAndApiVersion(project, module, IdeKotlinVersion.fromLanguageVersion(languageVersion), apiVersion = apiVersion)
    try {
        body()
    } finally {
        val bundledCompilerVersion = KotlinPluginLayout.standaloneCompilerVersion

        if (removeFacet) {
            KotlinCommonCompilerArgumentsHolder.getInstance(project)
                .update { this.languageVersion = bundledCompilerVersion.languageVersion.versionString }
            module.removeKotlinFacet(ProjectDataManager.getInstance().createModifiableModelsProvider(project), commitModel = true)
        } else {
            configureLanguageAndApiVersion(project, module, bundledCompilerVersion)
        }
    }
}

private fun configureLanguageAndApiVersion(
    project: Project,
    module: Module,
    compilerVersion: IdeKotlinVersion,
    languageVersion: LanguageVersion? = null,
    projectLanguageVersion: LanguageVersion? = null,
    apiVersion: ApiVersion? = null,
) {
    WriteAction.run<Throwable> {
        val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(project)
        val facet = module.getOrCreateFacet(modelsProvider, useProjectSettings = false)
        val facetSettings = facet.configuration.settings

        facetSettings.updateCompilerArguments {
            this.apiVersion = null
        }

        facet.configureFacet(compilerVersion, module.platform, modelsProvider)
        if (apiVersion != null) {
            facet.configuration.settings.apiLevel = LanguageVersion.fromVersionString(apiVersion.versionString)
        }
        if (languageVersion != null) {
            facet.configuration.settings.languageLevel = languageVersion
        }
        KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
            this.languageVersion = (projectLanguageVersion ?: languageVersion ?: compilerVersion.languageVersion).versionString
        }
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
    val parentContext = EditorActionHandler.caretDataContext(EditorUtil.getEditorDataContext(editor), caret)
    assertEquals(project, parentContext.getData(CommonDataKeys.PROJECT))
    assertEquals(editor, parentContext.getData(CommonDataKeys.EDITOR))
    return SimpleDataContext.builder()
        .additionalSteps()
        .setParent(parentContext)
        .build()
}
