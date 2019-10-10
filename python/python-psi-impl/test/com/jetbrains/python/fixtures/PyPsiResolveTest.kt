package com.jetbrains.python.fixtures

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyFunction

class PyPsiResolveTest : PyPsiResolveTestCase() {
  protected fun resolve(): PsiElement? {
    val ref = configureByFile("resolve/" + getTestName(false) + ".py")
    return ref?.resolve()
  }

  fun testFunc() {
    val targetElement = resolve()
    assertTrue(targetElement is PyFunction)
  }
}