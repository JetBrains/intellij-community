// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.dumpTextWithErrors
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.*
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractJavaToKotlinConverterMultiFileTest : AbstractJavaToKotlinConverterTest() {
    fun doTest(dirPath: String) {
        val directory = File(dirPath)
        val filesToConvert = directory.listFiles { _, name -> name.endsWith(".java") }!!
        val firstFileText = filesToConvert.first().readText()
        withCustomCompilerOptions(firstFileText, project, module) {
            doTest(directory, filesToConvert)
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun doTest(directory: File, filesToConvert: Array<File>) {
        val psiManager = PsiManager.getInstance(project)

        val psiFilesToConvert = filesToConvert.map { javaFile ->
            val expectedFileName = "${javaFile.nameWithoutExtension}.external"
            val expectedFiles = directory.listFiles { _, name ->
                name == "$expectedFileName.kt" || name == "$expectedFileName.java"
            }!!.filterNotNull()
            for (expectedFile in expectedFiles) {
                addFile(expectedFile, dirName = null)
            }

            val virtualFile = addFile(javaFile, "test")
            psiManager.findFile(virtualFile) as PsiJavaFile
        }

        val externalFiles = File(directory.absolutePath + File.separator + "external")
            .takeIf { it.exists() }
            ?.listFiles { _, name ->
                name.endsWith(".java") || name.endsWith(".kt")
            }.orEmpty()

        val externalPsiFiles = externalFiles.map { file ->
            val virtualFile = addFile(file, "test")
            val psiFile = psiManager.findFile(virtualFile)!!
            assert(psiFile is PsiJavaFile || psiFile is KtFile)
            psiFile
        }

        val j2kKind = if (KotlinPluginModeProvider.isK2Mode()) K2 else K1_NEW
        val extension = J2kConverterExtension.extension(j2kKind)
        val converter = extension.createJavaToKotlinConverter(project, module, ConverterSettings.defaultSettings)
        val postProcessor = extension.createPostProcessor()

        val (results, externalCodeProcessor) = converter.filesToKotlin(psiFilesToConvert, postProcessor)
        val process = externalCodeProcessor?.prepareWriteOperation(progress = null)

        fun expectedResultFile(i: Int) = File(filesToConvert[i].path.replace(".java", ".kt"))

        val resultFiles = psiFilesToConvert.mapIndexed { i, javaFile ->
            deleteFile(javaFile.virtualFile)
            val virtualFile = addFile(results[i], expectedResultFile(i).name, "test")
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
            process?.invoke()
        }

        for ((i, kotlinFile) in resultFiles.withIndex()) {
            KotlinTestUtils.assertEqualsToFile(expectedResultFile(i), kotlinFile.dumpTextWithErrors())
        }

        for ((externalFile, externalPsiFile) in externalFiles.zip(externalPsiFiles)) {
            val expectedFile = File(externalFile.path + ".expected")
            val resultText = when (externalPsiFile) {
                is KtFile -> externalPsiFile.dumpTextWithErrors()
                else -> externalPsiFile.text
            }
            KotlinTestUtils.assertEqualsToFile(expectedFile, resultText)
        }
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor()
}
