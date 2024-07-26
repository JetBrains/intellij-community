// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import org.jetbrains.annotations.ApiStatus

/**
 * @see [Version and Platform Checks](https://typing.readthedocs.io/en/latest/source/stubs.html.version-and-platform-checks)
 */
@ApiStatus.Internal
open class PyVersionAwareElementVisitor(private val languageLevel: LanguageLevel?) : PyRecursiveElementVisitor() {
  override fun visitPyIfStatement(node: PyIfStatement) {
    if (languageLevel == null) {
      super.visitPyIfStatement(node)
      return
    }
    val ifParts = sequenceOf(node.getIfPart()) + node.elifParts.asSequence()
    for (ifPart in ifParts) {
      val versionCheck = PyVersionCheck.fromCondition(ifPart)
      if (versionCheck == null) {
        super.visitPyIfStatement(node)
        return
      }
      if (versionCheck.matches(languageLevel)) {
        ifPart.statementList.accept(this)
        return
      }
    }
    node.elsePart?.statementList?.accept(this)
  }
}
