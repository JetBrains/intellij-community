// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils.ACTION_DIRECTIVE
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.TestFile
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

abstract class AbstractMavenUpdateConfigurationQuickFixTest(
  mavenVersion: String,
  modelVersion: String,
) : AbstractMavenImportingTest(mavenVersion, modelVersion) {

    override fun doTestAction(mainFile: TestFile) {
        val action = InTextDirectivesUtils.findStringWithPrefixes(mainFile.content, ACTION_DIRECTIVE)
            ?: error("'${ACTION_DIRECTIVE}' directive is not found")

        with(codeInsightTestFixture) {
            canChangeDocumentDuringHighlighting(true)
            launchAction(codeInsightTestFixture.findSingleIntention(action))
        }
    }

    protected suspend fun doTest(intentionName: String) {
        val pomVFile = maven.createProjectSubFile("pom.xml", File(getTestDataPath(), "pom.xml").readText())
        val sourceVFile = maven.createProjectSubFile("src/main/kotlin/src.kt", File(getTestDataPath(), "src.kt").readText())
        LocalFileSystem.getInstance().refreshFiles(listOf(pomVFile, sourceVFile))
        maven.projectPom = pomVFile
        maven.importProjectAsync(pomVFile)
        withContext(Dispatchers.EDT) {
            writeIntentReadAction {
                assertTrue(ModuleRootManager.getInstance(project.modules.single()).fileIndex.isInSourceContent(sourceVFile))
                with(codeInsightTestFixture) {
                    configureFromExistingVirtualFile(sourceVFile)
                    canChangeDocumentDuringHighlighting(true)
                    launchAction(codeInsightTestFixture.findSingleIntention(intentionName))
                }
                FileDocumentManager.getInstance().saveAllDocuments()
                checkResult(pomVFile)
            }
        }
    }

    protected fun checkResult(file: VirtualFile) {
        val expectedPath = File(getTestDataPath(), "pom.xml.after")
        val expectedContent = FileUtil.loadFile(expectedPath, true)
        val actualContent = LoadTextUtil.loadText(file).toString()
        if (actualContent != expectedContent) {
            throw FileComparisonFailedError("pom.xml doesn't match", expectedContent, actualContent, expectedPath.path)
        }
    }
}
