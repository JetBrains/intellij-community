// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.idea.actions.JavaToKotlinAction
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractJavaToKotlinConverterMultiFileTest : AbstractJavaToKotlinConverterTest() {
    override fun getProjectDescriptor(): KotlinWithJdkAndRuntimeLightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor()

    fun doTest(dirPath: String) {
        val directory = File(dirPath)
        val filesToConvert = directory.listFiles { _, name -> name.endsWith(".java") }!!.sortedBy { it.name }
        val firstFile = filesToConvert.first()

        IgnoreTests.runTestIfNotDisabledByFileDirective(firstFile.toPath(), getDisableTestDirective(pluginMode)) {
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

        runWithModalProgressBlocking(project, "") {
            JavaToKotlinAction.Handler.convertFiles(psiFilesToConvert, project, module, askExternalCodeProcessing = false)
        }

        val resultFiles = psiFilesToConvert.map {
            f -> f.containingDirectory.findFile(f.name.replace(".java", ".kt")) as KtFile
        }

        fun expectedResultFile(i: Int) = File(filesToConvert[i].path.replace(".java", ".kt"))

        for ((i, kotlinFile) in resultFiles.withIndex()) {
            val expectedFile = expectedResultFile(i)
            val actualText = dumpTextWithErrors(kotlinFile)
            KotlinTestUtils.assertEqualsToFile(expectedFile, actualText)
        }

        for ((externalFile, externalPsiFile) in externalFiles.zip(externalPsiFiles)) {
            val expectedFile = File(externalFile.path + ".expected")
            val resultText = when (externalPsiFile) {
                is KtFile -> dumpTextWithErrors(externalPsiFile)
                else -> externalPsiFile.text
            }
            KotlinTestUtils.assertEqualsToFile(expectedFile, resultText)
        }
    }

    abstract fun dumpTextWithErrors(kotlinFile: KtFile): String
}
