// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.test.kotlin

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockComponentManager
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.impl.PsiNameHelperImpl
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.io.URLUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.plugin.useK2Plugin
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.ConfigurationKind
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.testFramework.resetApplicationToNull
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.test.TestJdkKind
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastContext
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.java.JavaUastLanguagePlugin
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.KotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.evaluation.KotlinEvaluatorExtension
import org.jetbrains.uast.kotlin.internal.CliKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.UastAnalysisHandlerExtension
import java.io.File

abstract class AbstractKotlinUastTest : TestCase(),
                                        ExpectedPluginModeProvider {

    private lateinit var testRootDisposable: Disposable
    private lateinit var compilerConfiguration: CompilerConfiguration
    private lateinit var kotlinCoreEnvironment: KotlinCoreEnvironment

    protected val project: MockProject
        get() = kotlinCoreEnvironment.project as MockProject

    protected val uastContext: UastContext
        get() = project.getService(UastContext::class.java)

    protected val psiManager: PsiManager
        get() = PsiManager.getInstance(project)

    open var testDataDir: File = KotlinRoot.DIR.resolve("uast/uast-kotlin/tests/testData")

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1

    protected fun doTest(
        testName: String,
        checkCallback: (String, UFile) -> Unit = ::check,
    ) {
        val virtualFile = getVirtualFile(testName)

        val psiFile = psiManager.findFile(virtualFile) ?: error("Can't get psi file for $testName")
        val uFile = UastFacade.convertElementWithParent(psiFile, null) ?: error("Can't get UFile for $testName")
        checkCallback(testName, uFile as UFile)
    }

    protected abstract fun check(
        testName: String,
        file: UFile,
    )

    protected fun getVirtualFile(testName: String): VirtualFile {
        val testFile = testDataDir.listFiles { pathname -> pathname.nameWithoutExtension == testName }.first()

        if (ApplicationManager.getApplication() == null) {
            Disposer.register(testRootDisposable) {
                resetApplicationToNull()
            }
        }

        createEnvironment(testFile)

        initializeCoreEnvironment()
        initializeKotlinEnvironment()

        val trace = NoScopeRecordCliBindingTrace(project)

        TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
            project = project,
            files = kotlinCoreEnvironment.getSourceFiles(),
            trace = trace,
            configuration = compilerConfiguration,
            packagePartProvider = kotlinCoreEnvironment::createPackagePartProvider,
        )

        val vfs = VirtualFileManager.getInstance().getFileSystem(URLUtil.FILE_PROTOCOL)

        project.baseDir = vfs.findFileByPath(TEST_KOTLIN_MODEL_DIR.canonicalPath)

        return vfs.findFileByPath(testFile.canonicalPath)!!
    }

    private fun initializeCoreEnvironment() {
        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            UastLanguagePlugin.EP,
            UastLanguagePlugin::class.java,
        )

        CoreApplicationEnvironment.registerApplicationExtensionPoint(
            UEvaluatorExtension.EXTENSION_POINT_NAME,
            UEvaluatorExtension::class.java,
        )

        project.registerService(
            PsiNameHelper::class.java,
            PsiNameHelperImpl(project),
        )
        project.registerService(UastContext::class.java)

        Extensions.getRootArea().getExtensionPoint(UastLanguagePlugin.EP)
            .registerExtension(JavaUastLanguagePlugin())

        AnalysisHandlerExtension.registerExtension(
            project,
            UastAnalysisHandlerExtension(),
        )
    }

    private fun initializeKotlinEnvironment() {
        val area = Extensions.getRootArea()
        area.getExtensionPoint(UastLanguagePlugin.EP)
            .registerExtension(KotlinUastLanguagePlugin(), project)
        area.getExtensionPoint(UEvaluatorExtension.EXTENSION_POINT_NAME)
            .registerExtension(KotlinEvaluatorExtension(), project)

        val application = ApplicationManager.getApplication() as MockComponentManager
        application.registerService(
            BaseKotlinUastResolveProviderService::class.java,
            CliKotlinUastResolveProviderService::class.java,
        )
        project.registerService(
            KotlinUastResolveProviderService::class.java,
            CliKotlinUastResolveProviderService::class.java,
        )
    }

    private fun createEnvironment(source: File) {
        compilerConfiguration = createKotlinCompilerConfiguration(source)
        compilerConfiguration.put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)
        compilerConfiguration.put(CLIConfigurationKeys.PATH_TO_KOTLIN_COMPILER_JAR, TestKotlinArtifacts.kotlinCompiler)

        kotlinCoreEnvironment = KotlinCoreEnvironment.createForTests(
            parentDisposable = testRootDisposable,
            initialConfiguration = compilerConfiguration,
            extensionConfigs = EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )
    }

    override fun setUp() {
        super.setUp()
        testRootDisposable = Disposer.newDisposable()

        val oldUseK2Plugin = useK2Plugin
        useK2Plugin = pluginMode == KotlinPluginMode.K2
        Disposer.register(testRootDisposable) {
            useK2Plugin = oldUseK2Plugin
        }
    }

    @Suppress("SSBasedInspection")
    override fun tearDown() {
        try {
            runWriteActionAndWait { Disposer.dispose(testRootDisposable) }
        } finally {
            super.tearDown()
        }
    }

    private fun createKotlinCompilerConfiguration(sourceFile: File): CompilerConfiguration {
        return KotlinTestUtils.newConfiguration(ConfigurationKind.STDLIB_REFLECT, TestJdkKind.FULL_JDK).apply {
            addKotlinSourceRoot(sourceFile.canonicalPath)

            messageCollector = PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, true)

            put(CommonConfigurationKeys.MODULE_NAME, LightProjectDescriptor.TEST_MODULE_NAME)

            if (sourceFile.extension == KotlinParserDefinition.STD_SCRIPT_SUFFIX) {
                put(CommonConfigurationKeys.ALLOW_ANY_SCRIPTS_IN_SOURCE_ROOTS, true)
                loadScriptingPlugin(this)
            }
        }
    }
}

val TEST_KOTLIN_MODEL_DIR = KotlinRoot.DIR.resolve("uast/uast-kotlin/tests/testData")

private fun loadScriptingPlugin(configuration: CompilerConfiguration) {
    val pluginClasspath = listOf(
        TestKotlinArtifacts.kotlinScriptingCompiler,
        TestKotlinArtifacts.kotlinScriptingCompilerImpl,
        TestKotlinArtifacts.kotlinScriptingCommon,
        TestKotlinArtifacts.kotlinScriptingJvm
    )

    PluginCliParser.loadPluginsSafe(pluginClasspath.map { it.absolutePath }, emptyList(), emptyList(), configuration)
}
