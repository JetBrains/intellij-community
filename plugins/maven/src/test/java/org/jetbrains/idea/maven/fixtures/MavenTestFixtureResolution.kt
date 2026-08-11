// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.configTest
import com.intellij.maven.testFramework.fixtures.findPsiFile
import com.intellij.maven.testFramework.fixtures.getEditor
import com.intellij.maven.testFramework.fixtures.getElementAtCaret
import com.intellij.maven.testFramework.fixtures.getTestPsiFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlTagValue
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import junit.framework.TestCase.assertFalse
import org.jetbrains.idea.maven.dom.MavenDomElement
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.dom.references.MavenPsiElementWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame

// Reference resolution, DOM tag lookup, documentation and intention helpers.

suspend fun MavenDomTestFixture.getReferenceAtCaret(f: VirtualFile): PsiReference? {
  configTest(f)
  val editorOffset = doGetEditorOffset(f)
  val psiFile = findPsiFile(f)
  return readAction { psiFile.findReferenceAt(editorOffset) }
}

private suspend fun MavenDomTestFixture.getReferenceAt(f: VirtualFile, offset: Int): PsiReference? {
  configTest(f)
  val psiFile = findPsiFile(f)
  return readAction { psiFile.findReferenceAt(offset) }
}

suspend fun MavenDomTestFixture.getReference(file: VirtualFile, referenceText: String): PsiReference? {
  val text = VfsUtilCore.loadText(file)
  val index = text.indexOf(referenceText)
  assert(index >= 0)
  assert(text.indexOf(referenceText, index + referenceText.length) == -1) {
    "Reference text '$referenceText' occurs more than one times"
  }
  return getReferenceAt(file, index)
}

suspend fun MavenDomTestFixture.resolveReference(file: VirtualFile, referenceText: String): PsiElement? {
  val ref = getReference(file, referenceText)
  assertNotNull(ref)
  var resolved = readAction { ref!!.resolve() }
  if (resolved is MavenPsiElementWrapper) {
    resolved = resolved.wrappee
  }
  return resolved
}

suspend fun MavenDomTestFixture.resolveReference(file: VirtualFile, referenceText: String, index: Int): PsiElement? {
  var index = index
  val text = VfsUtilCore.loadText(file)
  var k = -1

  do {
    k = text.indexOf(referenceText, k + 1)
    assert(k >= 0) { index }
  }
  while (--index >= 0)

  val psiReference = getReferenceAt(file, k)!!
  return readAction { psiReference.resolve() }
}

suspend fun MavenDomTestFixture.assertResolved(file: VirtualFile, expected: PsiElement) {
  doAssertResolved(file, expected)
}

suspend fun MavenDomTestFixture.assertResolved(file: VirtualFile, expected: PsiElement, expectedText: String?) {
  val ref = doAssertResolved(file, expected)
  assertEquals(expectedText, readAction { ref!!.canonicalText })
}

private suspend fun MavenDomTestFixture.doAssertResolved(file: VirtualFile, expected: PsiElement): PsiReference? {
  assertNotNull("expected reference is null", expected)
  val ref = getReferenceAtCaret(file)
  assertNotNull("reference at caret is null", ref)
  var resolved = readAction { ref!!.resolve() }
  if (resolved is MavenPsiElementWrapper) {
    resolved = resolved.wrappee
  }
  val expectedText = readAction { expected.text }
  val resolvedText = readAction { resolved?.text }
  assertEquals(expectedText, resolvedText)
  return ref
}

suspend fun MavenDomTestFixture.assertUnresolved(file: VirtualFile) {
  val ref = getReferenceAtCaret(file)
  assertNotNull(ref)
  readAction { assertNull(ref!!.resolve()) }
}

suspend fun MavenDomTestFixture.assertUnresolved(file: VirtualFile, expectedText: String?) {
  val ref = getReferenceAtCaret(file)
  assertNotNull(ref)
  readAction {
    assertNull(ref!!.resolve())
    assertEquals(expectedText, ref.canonicalText)
  }
}

suspend fun MavenDomTestFixture.assertNoReferences(file: VirtualFile, refClass: Class<*>) {
  val ref = getReferenceAtCaret(file) ?: return
  val refs = if (ref is PsiMultiReference) ref.references else arrayOf(ref)
  readAction {
    for (each in refs) {
      assertFalse(each.toString(), refClass.isInstance(each))
    }
  }
}

suspend fun MavenDomTestFixture.findTag(file: VirtualFile, path: String, clazz: Class<out MavenDomElement> = MavenDomProjectModel::class.java): XmlTag {
  configTest(file)
  return readAction {
    val model = MavenDomUtil.getMavenDomModel(project, file, clazz)
    assertNotNull("Model is not of $clazz", model)
    val tag = MavenDomUtil.findTag(model!!, path)
    val xmlTag = model.xmlTag
    assertNotNull("xmlTag is null for $path", xmlTag)
    assertNotNull("Tag $path not found in \n${xmlTag!!.text}", tag)
    tag!!
  }
}

suspend fun MavenDomTestFixture.findTag(path: String): XmlTag = findTag(projectPom, path)

suspend fun MavenDomTestFixture.findTagValue(file: VirtualFile, path: String, clazz: Class<out MavenDomElement> = MavenDomProjectModel::class.java): XmlTagValue {
  val tag = findTag(file, path, clazz)
  return readAction { tag.value }
}

private suspend fun MavenDomTestFixture.doGetEditorOffset(f: VirtualFile): Int {
  configTest(f)
  return readAction { fixture.editor.caretModel.offset }
}

suspend fun MavenDomTestFixture.getEditorOffset(f: VirtualFile): Int {
  val editor = getEditor(f)
  return readAction { editor.caretModel.offset }
}

suspend fun MavenDomTestFixture.assertDocumentation(expectedText: String?) {
  val originalElement = getElementAtCaret(projectPom)
  val editor = fixture.editor
  val psiFile = getTestPsiFile(projectPom)
  readAction {
    val targetElement = DocumentationManager.getInstance(project).findTargetElement(editor, psiFile, originalElement)
    val provider = DocumentationManager.getProviderFromElement(targetElement)
    assertEquals(expectedText, provider.generateDoc(targetElement, originalElement))
    val lookupElement = provider.getDocumentationElementForLookupItem(
      PsiManager.getInstance(project), originalElement!!.text, originalElement)
    assertSame(targetElement, lookupElement)
  }
}

suspend fun MavenDomTestFixture.getIntentionAtCaret(intentionName: String): IntentionAction? {
  return getIntentionAtCaret(projectPom, intentionName)
}

suspend fun MavenDomTestFixture.getIntentionAtCaret(pomFile: VirtualFile, intentionName: String): IntentionAction? {
  configTest(pomFile)
  return CodeInsightTestUtil.findIntentionByText(fixture.availableIntentions, intentionName)
}

suspend fun MavenDomTestFixture.findPsiFileAndGetText(f: VirtualFile?): String? {
  if (f == null) return null
  return readAction { PsiManager.getInstance(project).findFile(f)?.text }
}