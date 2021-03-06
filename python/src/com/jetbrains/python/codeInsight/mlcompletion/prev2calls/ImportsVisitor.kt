// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion.prev2calls

import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportStatement
import com.jetbrains.python.psi.PyRecursiveElementVisitor

class ImportsVisitor(val fullNames: MutableMap<String, String> = mutableMapOf()): PyRecursiveElementVisitor() {
  override fun visitPyFromImportStatement(node: PyFromImportStatement) {
    super.visitPyFromImportStatement(node)

    val fromName = node.importSourceQName
    node.importElements.forEach { importElement ->
      val importedQName = importElement.importedQName.toString()
      val fullName = "$fromName.$importedQName"
      fullNames[importedQName] = fullName
      importElement.asName?.let { fullNames[it] = fullName }
    }
  }

  override fun visitPyImportStatement(node: PyImportStatement) {
    super.visitPyImportStatement(node)

    node.importElements.forEach { importElement ->
      val importedQName = importElement.importedQName.toString()
      importElement.asName?.let { fullNames[it] = importedQName }
    }
  }
}