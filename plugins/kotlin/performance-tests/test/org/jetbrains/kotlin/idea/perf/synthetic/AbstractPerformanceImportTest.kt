// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.perf.synthetic

import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.core.formatter.KotlinPackageEntry
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.testFramework.Stats
import org.jetbrains.kotlin.idea.testFramework.performanceTest
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractPerformanceImportTest : KotlinLightCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    protected abstract fun stats(): Stats

    override fun tearDown() {
        RunAll(
            ThrowableRunnable { super.tearDown() }
        ).run()
    }

    protected fun doPerfTest(unused: String) {
        val testName = getTestName(false)
        configureCodeStyleAndRun(project) {
            val fixture = myFixture
            val dependencySuffixes = listOf(".dependency.kt", ".dependency.java", ".dependency1.kt", ".dependency2.kt")
            for (suffix in dependencySuffixes) {
                val dependencyPath = fileName().replace(".kt", suffix)
                if (File(testDataPath, dependencyPath).exists()) {
                    fixture.configureByFile(dependencyPath)
                }
            }

            fixture.configureByFile(fileName())

            var file = fixture.file as KtFile

            var fileText = file.text
            val codeStyleSettings = file.kotlinCustomSettings
            codeStyleSettings.NAME_COUNT_TO_USE_STAR_IMPORT = InTextDirectivesUtils.getPrefixedInt(
                fileText,
                "// NAME_COUNT_TO_USE_STAR_IMPORT:"
            ) ?: nameCountToUseStarImportDefault

            codeStyleSettings.NAME_COUNT_TO_USE_STAR_IMPORT_FOR_MEMBERS = InTextDirectivesUtils.getPrefixedInt(
                fileText,
                "// NAME_COUNT_TO_USE_STAR_IMPORT_FOR_MEMBERS:"
            ) ?: nameCountToUseStarImportForMembersDefault

            codeStyleSettings.IMPORT_NESTED_CLASSES = InTextDirectivesUtils.getPrefixedBoolean(
                fileText,
                "// IMPORT_NESTED_CLASSES:"
            ) ?: false

            InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// PACKAGE_TO_USE_STAR_IMPORTS:").forEach {
                codeStyleSettings.PACKAGES_TO_USE_STAR_IMPORTS.addEntry(KotlinPackageEntry(it.trim(), false))
            }

            InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// PACKAGES_TO_USE_STAR_IMPORTS:").forEach {
                codeStyleSettings.PACKAGES_TO_USE_STAR_IMPORTS.addEntry(KotlinPackageEntry(it.trim(), true))
            }

            var descriptorName = InTextDirectivesUtils.findStringWithPrefixes(file.text, "// IMPORT:")
                ?: error("No IMPORT directive defined")

            var filter: (DeclarationDescriptor) -> Boolean = { true }
            if (descriptorName.startsWith("class:")) {
                filter = { it is ClassDescriptor }
                descriptorName = descriptorName.substring("class:".length).trim()
            }

            val fqName = FqName(descriptorName)
            val importInsertHelper = ImportInsertHelper.getInstance(project)
            val psiDocumentManager = PsiDocumentManager.getInstance(project)

            performanceTest<Unit, String> {
                name(testName)
                stats(stats())
                setUp {
                    fixture.configureByFile(fileName())
                    file = fixture.file as KtFile

                    fileText = file.text
                }
                test {
                    it.value = project.executeWriteCommand<String?>("") {
                        perfTestCore(file, fqName, filter, descriptorName, importInsertHelper, psiDocumentManager)
                    }
                }
                tearDown {
                    val log = it.value
                    val testPath = dataFilePath(fileName())
                    val afterFile = File("$testPath.after")
                    KotlinTestUtils.assertEqualsToFile(afterFile, fixture.file.text)
                    if (log != null) {
                        val logFile = File("$testPath.log")
                        if (log.isNotEmpty()) {
                            KotlinTestUtils.assertEqualsToFile(logFile, log)
                        } else {
                            assertFalse(logFile.exists())
                        }
                    }
                    runWriteAction {
                        myFixture.file.delete()
                    }
                }
            }
        }
    }

    abstract fun perfTestCore(
        file: KtFile,
        fqName: FqName,
        filter: (DeclarationDescriptor) -> Boolean,
        descriptorName: String,
        importInsertHelper: ImportInsertHelper,
        psiDocumentManager: PsiDocumentManager
    ): String?


    protected open val nameCountToUseStarImportDefault: Int
        get() = 1

    protected open val nameCountToUseStarImportForMembersDefault: Int
        get() = 3

}
