// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K2
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractJavaToKotlinConverterMultiFileTest : AbstractJavaToKotlinConverterTest() {
    override fun getProjectDescriptor(): KotlinWithJdkAndRuntimeLightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor()

    fun doTest(dirPath: String) {
        val directory = File(dirPath)
        val filesToConvert = directory.listFiles { _, name -> name.endsWith(".java") }!!.sortedBy { it.name }
        val firstFile = filesToConvert.first()

        IgnoreTests.runTestIfNotDisabledByFileDirective(firstFile.toPath(), getDisableTestDirective()) {
            withCustomCompilerOptions(firstFile.readText(), project, module) {
                doTest(directory, filesToConvert)
            }
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun doTest(directory: File, filesToConvert: List<File>) {
        val psiManager = PsiManager.getInstance(project)

        val psiFilesToConvert = filesToConvert.map { javaFile ->
            val virtualFile = addFile(javaFile, dirName = "test")
            psiManager.findFile(virtualFile) as PsiJavaFile
        }

        val externalFiles = File(directory.absolutePath + File.separator + "external")
            .takeIf { it.exists() }
            ?.listFiles { _, name ->
                name.endsWith(".java") || name.endsWith(".kt")
            }.orEmpty()

        val externalPsiFiles = externalFiles.map { file ->
            val virtualFile = addFile(file, dirName = "test")
            val psiFile = psiManager.findFile(virtualFile)!!
            assert(psiFile is PsiJavaFile || psiFile is KtFile)
            psiFile
        }

        var r: FilesResult? = null
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            { r = convertFilesToKotlin(psiFilesToConvert) }, "", true, project)

        val (results, externalCodeProcessor) = r!!
        val externalUsagesFixerProcess = externalCodeProcessor?.prepareWriteOperation(progress = null)

        fun expectedResultFile(i: Int) = File(filesToConvert[i].path.replace(".java", ".kt"))

        val resultFiles = psiFilesToConvert.mapIndexed { i, javaFile ->
            deleteFile(javaFile.virtualFile)
            val kotlinFileText = results[i].getTextWithoutDirectives()
            val virtualFile = addFile(kotlinFileText, expectedResultFile(i).name, dirName = "test")
            psiManager.findFile(virtualFile) as KtFile
        }

        resultFiles.forEach { it.commitAndUnblockDocument() }

        val contextElement = resultFiles.first()
        allowAnalysisOnEdt {
            analyze(contextElement) {
                externalCodeProcessor?.bindJavaDeclarationsToConvertedKotlinOnes(resultFiles)
            }
        }

        project.executeWriteCommand("") {
            externalUsagesFixerProcess?.invoke()
        }

        for ((i, kotlinFile) in resultFiles.withIndex()) {
            val expectedFile = expectedResultFile(i)
            val actualText = kotlinFile.getFileTextWithErrors()
            val actualTextWithoutRedundantImports = removeRedundantImports(actualText)
            KotlinTestUtils.assertEqualsToFile(expectedFile, actualTextWithoutRedundantImports)
        }

        for ((externalFile, externalPsiFile) in externalFiles.zip(externalPsiFiles)) {
            val expectedFile = File(externalFile.path + ".expected")
            val resultText = when (externalPsiFile) {
                is KtFile -> externalPsiFile.getFileTextWithErrors()
                else -> externalPsiFile.text
            }
            KotlinTestUtils.assertEqualsToFile(expectedFile, resultText)
        }
    }

    private fun convertFilesToKotlin(psiFilesToConvert: List<PsiJavaFile>): FilesResult {
        val j2kKind = if (KotlinPluginModeProvider.isK2Mode()) K2 else K1_NEW
        val extension = J2kConverterExtension.extension(j2kKind)
        val converter = extension.createJavaToKotlinConverter(project, module, ConverterSettings.defaultSettings)
        val postProcessor = extension.createPostProcessor()
        return converter.filesToKotlin(psiFilesToConvert, postProcessor)
    }
}
