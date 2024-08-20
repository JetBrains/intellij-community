// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.doWriteAction
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import org.jetbrains.kotlin.idea.test.KotlinCliCompilerFacade
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractIrKotlinScriptEvaluateExpressionTest : AbstractIrKotlinEvaluateExpressionTest() {

    override fun configureProjectByTestFiles(testFiles: List<TestFileWithModule>, testAppDirectory: File) {
        val scriptsSrcPath = listOf(testAppPath, SCRIPT_SOURCES_DIR).joinToString(File.separator)
        val virtualFile = File(scriptsSrcPath).refreshAndToVirtualFile()
            ?: error("Can't find virtual file $scriptsSrcPath for module ${module.name}")
        doWriteAction {
            PsiTestUtil.addSourceRoot(module, virtualFile)
        }
    }

    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        // Warm up the script configuration, otherwise it will be initialized during evaluation
        // which leads to "Evaluation is not possible during indexing" exception
        runInEdtAndWait { ScriptConfigurationManager.updateScriptDependenciesSynchronously(getScriptKtFile()) }
        super.doMultiFileTest(files, preferences)
    }

    override fun createBreakpoints(className: String?) = super.createBreakpoints(getScriptKtFile())

    override fun getMainClassName(compilerFacility: DebuggerTestCompilerFacility) = K2JVMCompiler::class.qualifiedName!!

    override fun createJavaParameters(mainClass: String?): JavaParameters {
        return super.createJavaParameters(mainClass).apply {
            val artifactsForCompiler = KotlinCliCompilerFacade.getTestArtifactsNeededForCLICompiler()
            artifactsForCompiler.forEach(classPath::add)

            val artifactsForScriptFile = listOf(
                TestKotlinArtifacts.kotlinStdlib,
                TestKotlinArtifacts.kotlinScriptRuntime
            )
            val classpath = artifactsForScriptFile.joinToString(File.pathSeparator) { it.absolutePath }
            programParametersList.addAll(
                "-script", getScriptKtFile().virtualFilePath,
                "-classpath", classpath,
                "-no-stdlib",
                "-language-version", chooseLanguageVersionForCompilation(compileWithK2).versionString,
            )
        }
    }

    // Only one script file is allowed
    private fun getScriptKtFile(): KtFile = sourcesKtFiles.scriptKtFiles.single()
}

abstract class AbstractK1IdeK2CodeScriptEvaluateExpressionTest : AbstractIrKotlinScriptEvaluateExpressionTest() {
    override val compileWithK2 = true
    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY

    override fun createJavaParameters(mainClass: String?): JavaParameters {
        return super.createJavaParameters(mainClass).apply {
            val languageVersion = chooseLanguageVersionForCompilation(useK2 = true)
            programParametersList.add("-language-version=$languageVersion")
        }
    }
}
