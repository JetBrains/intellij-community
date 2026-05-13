// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework

import com.intellij.python.junit5Tests.framework.metaInfo.TestMetaInfoExtension.Companion.getTestClassInfo
import com.intellij.python.junit5Tests.framework.metaInfo.TestMetaInfoExtension.Companion.setTestCaseFilePath
import com.intellij.python.junit5Tests.framework.metaInfo.resolveTestName
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.isDirectory
import kotlin.io.path.relativeTo
import kotlin.jvm.optionals.getOrNull


/**
 * Test annotation used together with JUnit 5's [TestTemplate] to run a test method
 * once per file in a folder. Each matching file becomes a parameterized test invocation.
 *
 * ## Folder location
 *
 * The folder is `<TestDataPath>/<testName>/`, where `<testName>` is derived from the
 * test method name by [com.intellij.testFramework.PlatformTestUtil.getTestName]: the
 * optional leading `test` is stripped and the first letter is lower-cased. Examples:
 *
 * - `fun testFoo()` → `<TestDataPath>/foo/`
 * - `fun allTests()` → `<TestDataPath>/allTests/`
 *
 * Each file in that folder that matches [fileNameFilter] becomes one invocation.
 *
 * Because this annotation is itself a [TestTemplate], do not add `@Test` to the
 * method; JUnit 5 picks it up directly.
 *
 * Processed by [AllFilesInFolderTestCaseProvider].
 *
 * @param fileNameFilter Regex applied to file names in the folder. Defaults to
 *   [FileNameFilter.ALL_FILES] (every file).
 */
@TestOnly
@TestTemplate
@ExtendWith(AllFilesInFolderTestCaseProvider::class)
annotation class FolderTest(val fileNameFilter: String = FileNameFilter.ALL_FILES)

@TestOnly
@Suppress("unused")
class FileNameFilter {
  companion object {
    const val ALL_FILES = """^.*$"""
    const val PYTHON = """^.*\.(py)$"""
  }
}

private class AllFilesInFolderTestCaseProvider : TestTemplateInvocationContextProvider {
  private fun getTestFolderPath(context: ExtensionContext): Path {
    val metaInfo = context.getTestClassInfo()
    val testName = context.resolveTestName()

    val testResourcePath = metaInfo.getTestResourcePath(fileName = testName)
    return testResourcePath?.takeIf { it.isDirectory() }
           ?: error("Please make a folder with tests for \"${context.testMethod.getOrNull()?.name}\": ${metaInfo.testDataPath}/${testName}")
  }

  override fun supportsTestTemplate(context: ExtensionContext): Boolean {
    return true
  }

  override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext?>? {
    val testFolderPath = getTestFolderPath(context)

    val folderTest = context.testMethod?.getOrNull()?.getAnnotation(FolderTest::class.java)
                     ?: error("there is no ${FolderTest::class.java} annotation, can't get file name pattern")
    val fileNamePattern = folderTest.fileNameFilter.toRegex()

    return Files.list(testFolderPath).filter { it.fileName.toString() matches fileNamePattern }.map { filePath ->
      val path = filePath.relativeTo(testFolderPath.parent)
      context.setTestCaseFilePath(path)

      object : TestTemplateInvocationContext {
        override fun getDisplayName(invocationIndex: Int): String? {
          return "[$invocationIndex] ${filePath.fileName}"
        }
      }
    }
  }
}
