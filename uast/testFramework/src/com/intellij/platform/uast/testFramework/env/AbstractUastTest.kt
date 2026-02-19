// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.uast.testFramework.env

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.UastVisitor

abstract class AbstractUastFixtureTest : LightJavaCodeInsightFixtureTestCase() {
  abstract fun check(testName: String, file: UFile)

  @Suppress("NAME_SHADOWING")
  fun doTest(testName: String, checkCallback: (String, UFile) -> Unit = { testName, file -> check(testName, file) }) {
    val uFile = configureUFile(testName)
    checkCallback(testName, uFile)
  }

  fun configureUFile(testName: String): UFile {
    val psiFile = myFixture.configureByFile(testName)
    val uFile = UastFacade.convertElementWithParent(psiFile, null) ?: error("Can't get UFile for $testName")
    assertNotNull(uFile)
    assertTrue(uFile.toString(), uFile is UFile)
    return uFile as UFile
  }
}


fun <T> UElement.findElementByText(refText: String, cls: Class<T>): T {
  val matchingElements = mutableListOf<T>()
  accept(object : UastVisitor {
    override fun visitElement(node: UElement): Boolean {
      if (cls.isInstance(node) && node.sourcePsi?.text == refText) {
        @Suppress("UNCHECKED_CAST")
        matchingElements.add(node as T)
      }
      return false
    }
  })

  if (matchingElements.isEmpty()) {
    throw IllegalArgumentException("Reference '$refText' not found")
  }
  if (matchingElements.size != 1) {
    throw IllegalArgumentException("Reference '$refText' is ambiguous")
  }
  return matchingElements.single()
}

inline fun <reified T : Any> UElement.findElementByText(refText: String): T = findElementByText(refText, T::class.java)

inline fun <reified T : UElement> UElement.findElementByTextFromPsi(refText: String, strict: Boolean = true): T =
  (this.sourcePsi ?: throw AssertionError("no sourcePsi for $this")).findUElementByTextFromPsi(refText, strict)

inline fun <reified T : UElement> PsiElement.findUElementByTextFromPsi(refText: String, strict: Boolean = true): T {
  val elementAtStart = this.findElementAt(this.text.indexOf(refText))
                       ?: throw AssertionError("requested text '$refText' was not found in $this")
  val uElementContainingText = generateSequence(elementAtStart) { if (it is PsiFile) null else it.parent }
    .let { if (strict) it.dropWhile { e -> !e.text.contains(refText) } else it }
    .mapNotNull { it.toUElementOfType<T>() }
    .firstOrNull() ?: throw AssertionError("requested text '$refText' not found as ${T::class.java}")
  if (strict && uElementContainingText.sourcePsi != null && uElementContainingText.sourcePsi?.text != refText) {
    throw AssertionError("requested text '$refText' found as '${uElementContainingText.sourcePsi?.text}' in $uElementContainingText")
  }
  return uElementContainingText
}