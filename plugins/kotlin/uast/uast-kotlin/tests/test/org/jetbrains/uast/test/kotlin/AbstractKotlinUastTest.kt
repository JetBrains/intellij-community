// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin

import com.intellij.mock.MockComponentManager
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.io.URLUtil
import org.jetbrains.kotlin.idea.checkers.CompilerTestLanguageVersionSettings
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.idea.test.ConfigurationKind
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestJdkKind
import org.jetbrains.kotlin.idea.test.testFramework.resetApplicationToNull
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.KotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.evaluation.KotlinEvaluatorExtension
import org.jetbrains.uast.kotlin.internal.CliKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.UastAnalysisHandlerExtension
import org.jetbrains.uast.test.env.AbstractCoreEnvironment
import org.jetbrains.uast.test.kotlin.env.AbstractUastTest
import java.io.File

abstract class AbstractKotlinUastTest : AbstractUastTest() {

    private lateinit var compilerConfiguration: CompilerConfiguration
    private var kotlinCoreEnvironment: KotlinCoreEnvironment? = null

    open var testDataDir: File = KotlinRoot.DIR.resolve("uast/uast-kotlin/tests/testData")

    override fun getVirtualFile(testName: String): VirtualFile {
        val testFile = testDataDir.listFiles { pathname -> pathname.nameWithoutExtension == testName }.first()

        super.initializeEnvironment(testFile)

        initializeKotlinEnvironment()

        val trace = NoScopeRecordCliBindingTrace()

        val environment = kotlinCoreEnvironment!!
        TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
            project, environment.getSourceFiles(), trace, compilerConfiguration, environment::createPackagePartProvider
        )

        val vfs = VirtualFileManager.getInstance().getFileSystem(URLUtil.FILE_PROTOCOL)

        val ideaProject = project
        ideaProject.baseDir = vfs.findFileByPath(TEST_KOTLIN_MODEL_DIR.canonicalPath)

        return vfs.findFileByPath(testFile.canonicalPath)!!
    }

    private fun initializeKotlinEnvironment() {
        val area = Extensions.getRootArea()
        area.getExtensionPoint(UastLanguagePlugin.extensionPointName).registerExtension(KotlinUastLanguagePlugin(), project)
        area.getExtensionPoint(UEvaluatorExtension.EXTENSION_POINT_NAME).registerExtension(KotlinEvaluatorExtension(), project)

        val application = ApplicationManager.getApplication() as MockComponentManager
        application.registerService(
            BaseKotlinUastResolveProviderService::class.java,
            CliKotlinUastResolveProviderService::class.java
        )
        project.registerService(
            KotlinUastResolveProviderService::class.java,
            CliKotlinUastResolveProviderService::class.java
        )
    }

    override fun createEnvironment(source: File): AbstractCoreEnvironment {
        val appWasNull = ApplicationManager.getApplication() == null
        compilerConfiguration = createKotlinCompilerConfiguration(source)
        compilerConfiguration.put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)
        compilerConfiguration.put(CLIConfigurationKeys.PATH_TO_KOTLIN_COMPILER_JAR, TestKotlinArtifacts.kotlinCompiler)

        val parentDisposable = Disposer.newDisposable()
        val kotlinCoreEnvironment =
            KotlinCoreEnvironment.createForTests(parentDisposable, compilerConfiguration, EnvironmentConfigFiles.JVM_CONFIG_FILES)

        this.kotlinCoreEnvironment = kotlinCoreEnvironment

        AnalysisHandlerExtension.registerExtension(
            kotlinCoreEnvironment.project, UastAnalysisHandlerExtension()
        )

        return KotlinCoreEnvironmentWrapper(kotlinCoreEnvironment, parentDisposable, appWasNull)
    }

    override fun tearDown() {
        kotlinCoreEnvironment = null
        super.tearDown()
    }

    private fun createKotlinCompilerConfiguration(sourceFile: File): CompilerConfiguration {
        return KotlinTestUtils.newConfiguration(ConfigurationKind.STDLIB_REFLECT, TestJdkKind.FULL_JDK).apply {
            addKotlinSourceRoot(sourceFile.canonicalPath)

            val messageCollector = PrintingMessageCollector(System.err, MessageRenderer.PLAIN_RELATIVE_PATHS, true)
            put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)

            put(CommonConfigurationKeys.MODULE_NAME, LightProjectDescriptor.TEST_MODULE_NAME)

            if (sourceFile.extension == KotlinParserDefinition.STD_SCRIPT_SUFFIX) {
                put(CommonConfigurationKeys.ALLOW_ANY_SCRIPTS_IN_SOURCE_ROOTS, true)
                loadScriptingPlugin(this)
            }
        }
    }

    private class KotlinCoreEnvironmentWrapper(
        val environment: KotlinCoreEnvironment,
        val parentDisposable: Disposable,
        val appWasNull: Boolean
    ) : AbstractCoreEnvironment() {
        override fun addJavaSourceRoot(root: File) {
            TODO("not implemented")
        }

        override fun addJar(root: File) {
            TODO("not implemented")
        }

        override val project: MockProject
            get() = environment.project as MockProject

        override fun dispose() {
            Disposer.dispose(parentDisposable)
            if (appWasNull) {
                resetApplicationToNull()
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
