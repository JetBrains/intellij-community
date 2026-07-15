// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.maven

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.QuickFix
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.setRawPomFile
import com.intellij.maven.testFramework.fixtures.setupJdkForModule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.inspections.runInspection
import org.jetbrains.kotlin.idea.maven.inspections.KotlinMavenPluginPhaseInspection
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import org.jetbrains.kotlin.utils.keysToMap
import org.junit.jupiter.api.Assertions
import java.io.File

/**
 * JUnit 5 base for the generated Kotlin Maven inspection tests, replacing the legacy JUnit 3/4
 * `KotlinMavenImportingTestCase`-based version. Concrete tests are generated (see `KotlinMavenInspectionTestGenerated`)
 * and parameterized over Maven versions via [KotlinMavenImportingTestBase].
 */
abstract class AbstractKotlinMavenInspectionTest(
    mavenVersion: String,
    modelVersion: String,
) : KotlinMavenImportingTestBase(mavenVersion, modelVersion) {

    /**
     * Bridge invoked by the generated `@Test` methods: resolves [testDataFilePath] against the class's `@TestRoot`
     * (mirroring the legacy `KotlinTestUtils.runTest`, which is unusable here because it requires a `junit.framework.TestCase`).
     */
    protected fun runTest(testDataFilePath: String) {
        val testRoot = TestMetadataUtil.getTestRoot(javaClass) ?: error("@TestRoot annotation was not found on ${javaClass.name}")
        runBlocking { doTest(File(testRoot, testDataFilePath).path) }
    }

    suspend fun doTest(fileName: String) {
        val pomFile = File(fileName)
        val pomText = pomFile.readText()

        maven.setRawPomFile(pomText)
        maven.importProjectAsync()
        edtWriteAction {
            project.modules.forEach { maven.setupJdkForModule(it.name) }
        }

        if (MKJAVA_REGEX.containsMatchIn(pomText)) {
            mkJavaFile()
        }

        val inspectionClassName = INSPECTION_REGEX.find(pomText)?.groups?.get(1)?.value
            ?: KotlinMavenPluginPhaseInspection::class.qualifiedName!!
        val inspectionClass = Class.forName(inspectionClassName)

        val expectedProblemsText = pomText.lines()
            .filter { PROBLEM_REGEX.matches(it) }
            .joinToString("\n")

        // The global inspection must run on the EDT without write access: InspectionTestUtil.runTool pumps the IDE event
        // queue (PlatformTestUtil.assertDispatchThreadWithoutWriteAccess). PSI is then read back under a read action.
        val presentation = withContext(Dispatchers.EDT) { runInspection(inspectionClass, project) }
        val actual = readAction {
            val problemElements = presentation.problemElements
            problemElements
                .keys()
                .filter { it.name == "pom.xml" }
                .map { problemElements.get(it) }
                .flatMap { it.toList() }
                .mapNotNull { it as? ProblemDescriptorBase }
                .map { SimplifiedProblemDescription(it.descriptionTemplate, it.psiElement.text.replace(WHITESPACE_REGEX, "")) to it }
                .sortedBy { it.first.text }
        }

        val actualProblemsText = actual.joinToString("\n") { "<!-- problem: on ${it.first.elementText}, title ${it.first.text} -->" }

        Assertions.assertEquals(expectedProblemsText, actualProblemsText)

        val suggestedFixes = actual.flatMap { p -> p.second.fixes?.sortedBy { it.familyName }?.map { p.second to it } ?: emptyList() }

        val filenamePrefix = pomFile.nameWithoutExtension + ".fixed."
        val fixFiles = pomFile.parentFile
            .listFiles { _, name -> name.startsWith(filenamePrefix) && name.endsWith(".xml") }
            .orEmpty()
            .sortedBy { it.name }

        val rangesToFixFiles: Map<File, IntRange> = fixFiles.keysToMap { file ->
            val fixFileName = file.name
            val fixRangeStr = fixFileName.substringBeforeLast('.').substringAfterLast('.')
            val numbers = fixRangeStr.split('-').map { it.toInt() }
            when (numbers.size) {
                0 -> error("No number in fix file $fixFileName")
                1 -> IntRange(numbers[0], numbers[0])
                2 -> IntRange(numbers[0], numbers[1])
                else -> error("Bad range `$fixRangeStr` in fix file $fixFileName")
            }
        }

        val sortedFixRanges = rangesToFixFiles.values.sortedBy { it.first }
        sortedFixRanges.forEachIndexed { i, range ->
            if (i > 0) {
                val previous = sortedFixRanges[i - 1]
                if (previous.last + 1 != range.first) {
                    error("Bad ranges in fix files: $previous and $range")
                }
            }
        }

        val numberOfFixDataFiles = sortedFixRanges.lastOrNull()?.endInclusive ?: 0
        if (numberOfFixDataFiles > suggestedFixes.size) {
            Assertions.fail<Unit>("Not all fixes were suggested by the inspection: expected count: ${fixFiles.size}, actual fixes count: ${suggestedFixes.size}")
        }
        if (numberOfFixDataFiles < suggestedFixes.size) {
            Assertions.fail<Unit>("Not all fixes covered by *.fixed.N.xml files")
        }

        withContext(Dispatchers.EDT) {
            writeIntentReadAction {
                val documentManager = PsiDocumentManager.getInstance(project)
                val document = documentManager.getDocument(PsiManager.getInstance(project).findFile(maven.projectPom)!!)!!
                val originalText = document.text

                suggestedFixes.forEachIndexed { index, suggestedFix ->
                    val (problem, quickfix) = suggestedFix
                    val file = rangesToFixFiles.entries.first { (_, range) -> index + 1 in range }.key

                    applyFix(quickfix, problem, pomFile.nameWithoutExtension)

                    KotlinTestUtils.assertEqualsToFile(file, document.text.trim())

                    ApplicationManager.getApplication().runWriteAction {
                        document.setText(originalText)
                        documentManager.commitDocument(document)
                    }
                }
            }
        }
    }

    private fun <D : ProblemDescriptor> applyFix(quickFix: QuickFix<in D>, desc: D, testName: String) {
        CommandProcessor.getInstance().executeCommand(
            project,
            {
                ApplicationManager.getApplication().runWriteAction {
                    quickFix.applyFix(project, desc)

                    val manager = PsiDocumentManager.getInstance(project)
                    val document = manager.getDocument(PsiManager.getInstance(project).findFile(maven.projectPom)!!)!!
                    manager.doPostponedOperationsAndUnblockDocument(document)
                    manager.commitDocument(document)
                    FileDocumentManager.getInstance().saveDocument(document)
                }
            },
            "quick-fix-$testName", "Kotlin",
        )
    }

    private suspend fun mkJavaFile() {
        edtWriteAction {
            val module = project.modules.single()
            val contentEntry = ModuleRootManager.getInstance(module).contentEntries.single()
            val sourceFolder = contentEntry.getSourceFolders(JavaSourceRootType.SOURCE).singleOrNull()
                ?: contentEntry.getSourceFolders(SourceKotlinRootType).singleOrNull()
            val javaFile = sourceFolder?.file?.toPsiDirectory(project)?.createFile("Test.java") ?: throw IllegalStateException()
            javaFile.viewProvider.document!!.setText("class Test {}\n")
        }

        readAction {
            Assertions.assertTrue(FileTypeIndex.containsFileOfType(JavaFileType.INSTANCE, project.modules.single().moduleScope))
        }
    }

    private data class SimplifiedProblemDescription(val text: String, val elementText: String)

    companion object {
        private val MKJAVA_REGEX = "<!--\\s*mkjava\\s*-->".toRegex(RegexOption.MULTILINE)
        private val INSPECTION_REGEX = "<!--\\s*inspection:\\s*([\\S]+)\\s-->".toRegex()
        private val PROBLEM_REGEX = "<!--\\s*problem:\\s*on\\s*([^,]+),\\s*title\\s*(.+)\\s*-->".toRegex()
        private val WHITESPACE_REGEX = "\\s+".toRegex()
    }
}
