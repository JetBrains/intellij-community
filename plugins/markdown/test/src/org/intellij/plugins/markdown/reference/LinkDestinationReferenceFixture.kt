// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.reference

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Path

private fun <T> T?.convertToNonNullWithAssert(): T {
  assertNotNull(this)
  return this!!
}

/**
 * Creates a [LinkDestinationReferenceFixture] on a new module of [projectFixture].
 * The setup copies the test data directory [testDataPath] to the module.
 * Declare the result as an instance field, so that each test gets its own fixture.
 */
@TestOnly
internal fun linkDestinationReferenceFixture(
  projectFixture: TestFixture<Project>,
  testDataPath: String,
  linksFilePath: String,
): TestFixture<LinkDestinationReferenceFixture> = testFixture("linkDestinationReferenceFixture") { context ->
  val fixture = codeInsightModuleFixture(projectFixture).init()
  fixture.testDataPath = testDataPath
  fixture.copyDirectoryToProject("", "")
  val testName = PlatformTestUtil.getTestName(context.extensionContext.requiredTestMethod.name, true)
  initialized(LinkDestinationReferenceFixture(fixture, testName, linksFilePath)) {}
}

/**
 * The helpers of a link destination reference test: they check the links of the links file and rename files.
 * Create it with [linkDestinationReferenceFixture].
 *
 * A link of the links file has the name of the test in brackets, for example `[near]`.
 * Call the helpers off the EDT. They switch to the EDT where they need it.
 */
@TestOnly
internal class LinkDestinationReferenceFixture internal constructor(
  /**
   * The code insight fixture of the test, on a new module for each test.
   */
  val fixture: CodeInsightTestFixture,
  private val testName: String,
  private val linksFilePath: String,
) {
  private fun findFile(path: Path): PsiFileSystemItem? {
    return PsiUtilCore.findFileSystemItem(fixture.project, fixture.findFileInTempDir(FileUtil.toSystemIndependentName(path.toString())))
  }

  private inline fun testReferenceToFile(filePathFirst: String,
                                         vararg filePathMore: String,
                                         crossinline assertions: (PsiReference?, PsiFileSystemItem) -> Unit) =
    testReferenceToFile(Path.of(filePathFirst, *filePathMore), assertions)

  private inline fun testReferenceToFile(filePath: Path, crossinline assertions: (PsiReference?, PsiFileSystemItem) -> Unit) {
    fixture.configureByFile(linksFilePath)
    timeoutRunBlocking {
      readAction {
        val reference = with(fixture) {
          val indexOfLink = file.text.indexOf("[$testName]")
          assertNotEquals(indexOfLink, -1)
          file.findReferenceAt(editor.caretModel.allCarets.map(Caret::getOffset).first { it >= indexOfLink })
        }
        assertions(reference, findFile(filePath).convertToNonNullWithAssert())
      }
    }
  }

  /**
   * Checks that the link `[<test name>]` of the links file is a reference to the file at the path.
   */
  fun testIsReferenceToFile(targetPathFirst: String, vararg targetPathMore: String) =
    testReferenceToFile(targetPathFirst, *targetPathMore) { reference, file ->
      assertTrue(reference.convertToNonNullWithAssert().isReferenceTo(file))
    }

  /**
   * Checks that the link `[<test name>]` of the links file is not a reference to the file at the path.
   */
  fun testIsNotReferenceToFile(undesiredTargetPathFirst: String, vararg undesiredTargetPathMore: String) =
    testReferenceToFile(undesiredTargetPathFirst, *undesiredTargetPathMore) { reference, file ->
      reference?.let { assertFalse(it.isReferenceTo(file)) }
    }

  /**
   * Checks that the link `[<test name>]` of the links file resolves to the header [headerName] of the file at [filePath].
   */
  fun testIsReferenceToHeader(filePath: Path, headerName: String) =
    testReferenceToFile(filePath) { reference, file ->
      val header = assertInstanceOf(MarkdownHeader::class.java, reference.convertToNonNullWithAssert().resolve())
      assertTrue(fixture.psiManager.areElementsEquivalent(file, header.containingFile))
      assertEquals(headerName, header.name)
    }

  /**
   * Opens `<test name>.md`, renames the file at [path] to [newName], and compares the result with `<test name>After.md`.
   */
  fun testRenameFile(path: Path, newName: String) {
    val extension = MarkdownFileType.INSTANCE.defaultExtension
    fixture.configureByFile("$testName.$extension")
    timeoutRunBlocking {
      val target = readAction { findFile(path).convertToNonNullWithAssert() }
      withContext(Dispatchers.EDT) {
        fixture.renameElement(target, newName)
      }
    }
    fixture.checkResultByFile("${testName}After.$extension")
  }
}
