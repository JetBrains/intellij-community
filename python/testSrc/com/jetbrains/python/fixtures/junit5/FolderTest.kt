// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures.junit5

import com.jetbrains.python.fixtures.junit5.metaInfo.TestMetaInfoExtension.Companion.getTestClassInfo
import com.jetbrains.python.fixtures.junit5.metaInfo.TestMetaInfoExtension.Companion.setTestCaseFilePath
import com.jetbrains.python.fixtures.junit5.metaInfo.resolveTestName
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
 * Test annotation that is used in conjunction with JUnit 5's [TestTemplate] to enable
 * parameterized testing over files within a directory. Each test invocation corresponds
 * to an individual file that matches the specified filter criteria.
 *
 * This annotation is processed by the [AllFilesInFolderTestCaseProvider] extension
 *
 * @param [fileNameFilter]: A string representing the regex pattern used to filter files
 *   in the folder for test execution. The default value is `FileNameFilter.ALL_FILES`,
 *   which means all files are included.
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
