// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.openapi.util.Version
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyIfStatement
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import org.jetbrains.annotations.ApiStatus

/**
 * @see [Version and Platform Checks](https://typing.readthedocs.io/en/latest/source/stubs.html.version-and-platform-checks)
 */
@ApiStatus.Internal
open class PyVersionAwareElementVisitor(languageLevel: LanguageLevel?) : PyRecursiveElementVisitor() {
  private val version = languageLevel?.let { Version(it.majorVersion, it.minorVersion, 0) }

  override fun visitPyIfStatement(node: PyIfStatement) {
    if (version == null) {
      super.visitPyIfStatement(node)
      return
    }
    val ifParts = sequenceOf(node.ifPart) + node.elifParts.asSequence()
    for (ifPart in ifParts) {
      val versions = ifPart.condition?.let(PyVersionCheck::convertToVersionRanges)
      if (versions == null) {
        super.visitPyIfStatement(node)
        return
      }
      if (versions.contains(version)) {
        ifPart.statementList.accept(this)
        return
      }
    }
    node.elsePart?.statementList?.accept(this)
  }
}
