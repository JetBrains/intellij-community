// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.assertUnorderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.configTest
import com.intellij.maven.testFramework.fixtures.getEditor
import com.intellij.maven.testFramework.fixtures.getTestPsiFile
import com.intellij.maven.testFramework.fixtures.refreshFiles
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.indices.MavenSystemIndicesManager
import org.junit.Assert.assertNotNull
import org.junit.ComparisonFailure

// Highlighting checks. [MavenDomTestFixture.Highlight] is the expected-highlight matcher.

suspend fun MavenDomTestFixture.checkHighlighting() {
  checkHighlighting(projectPom)
}

suspend fun MavenDomTestFixture.checkHighlighting(f: VirtualFile) {
  if (null != indices) {
    MavenSystemIndicesManager.getInstance().waitAllGavsUpdatesCompleted()
  }
  configTest(f)
  try {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        fixture.testHighlighting(true, false, true, f)
      }
    }
  }
  catch (e: ComparisonFailure) {
    throw e
  }
  catch (throwable: Throwable) {
    throw RuntimeException(throwable)
  }
}

suspend fun MavenDomTestFixture.checkHighlighting(file: VirtualFile, vararg expectedHighlights: MavenDomTestFixture.Highlight) {
  assertHighlighting(doHighlighting(file), *expectedHighlights)
}

suspend fun MavenDomTestFixture.doHighlighting(file: VirtualFile): Collection<HighlightInfo> {
  return withContext(Dispatchers.EDT) {
    writeIntentReadAction {
      refreshFiles(listOf(file))
      fixture.openFileInEditor(file)
      fixture.doHighlighting()
    }
  }
}

fun MavenDomTestFixture.assertHighlighting(highlightingInfos: Collection<HighlightInfo>, vararg expectedHighlights: MavenDomTestFixture.Highlight) {
  expectedHighlights.forEach { expected ->
    assertNotNull("Not highlighted: $expected", highlightingInfos.firstOrNull { expected.matches(it) })
  }
}

internal suspend fun MavenDomTestFixture.assertHighlighted(file: VirtualFile, vararg expected: HighlightPointer) {
  val editor = getEditor(file)
  val psiFile = getTestPsiFile(file)
  withContext(Dispatchers.EDT) {
    //readaction is not enough
    writeIntentReadAction {
      HighlightUsagesHandler.invoke(project, editor, psiFile)
    }
  }

  val highlighters = editor.markupModel.allHighlighters
  val actual: MutableList<HighlightPointer> = ArrayList()
  for (each in highlighters) {
    if (!each.isValid) continue
    val offset = each.startOffset
    val elementAtOffset = readAction { psiFile.findElementAt(offset) }
    val element = readAction {
      PsiTreeUtil.getParentOfType(elementAtOffset, XmlTag::class.java)
    }
    val text = editor.document.text.substring(offset, each.endOffset)
    actual.add(HighlightPointer(element, text))
  }

  assertUnorderedElementsAreEqual(actual, *expected)
}

internal class HighlightPointer(var element: PsiElement?, var text: String?) {
  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false

    val that = o as HighlightPointer

    if (if (element != null) element != that.element else that.element != null) return false
    if (if (text != null) text != that.text else that.text != null) return false

    return true
  }

  override fun hashCode(): Int {
    var result = if (element != null) element.hashCode() else 0
    result = 31 * result + (if (text != null) text.hashCode() else 0)
    return result
  }

  override fun toString(): String {
    return "HighlightInfo{" +
           "element=" + element +
           ", text='" + text + '\'' +
           '}'
  }
}

