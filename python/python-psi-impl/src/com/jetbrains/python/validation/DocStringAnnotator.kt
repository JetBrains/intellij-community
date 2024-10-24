/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.validation

import com.intellij.lang.annotation.HighlightSeverity
import com.jetbrains.python.PyNames
import com.jetbrains.python.documentation.docstrings.*
import com.jetbrains.python.highlighting.PyHighlighter
import com.jetbrains.python.psi.*

/**
 * Highlights doc strings in classes, functions, and files.
 */
class DocStringAnnotator : PyAnnotator() {
  override fun visitPyFile(node: PyFile) {
    annotateDocStringStmt(DocStringUtil.findDocStringExpression(node))
  }

  override fun visitPyFunction(node: PyFunction) {
    annotateDocStringStmt(DocStringUtil.findDocStringExpression(node.statementList))
  }

  override fun visitPyClass(node: PyClass) {
    annotateDocStringStmt(DocStringUtil.findDocStringExpression(node.statementList))
  }

  override fun visitPyAssignmentStatement(node: PyAssignmentStatement) {
    if (node.isAssignmentTo(PyNames.DOC)) {
      val right = node.assignedValue
      if (right is PyStringLiteralExpression) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(right).textAttributes(PyHighlighter.PY_DOC_COMMENT).create()
        annotateDocStringStmt(right)
      }
    }
  }

  override fun visitPyExpressionStatement(node: PyExpressionStatement) {
    if (node.expression is PyStringLiteralExpression &&
        DocStringUtil.isVariableDocString(node.expression as PyStringLiteralExpression)
    ) {
      annotateDocStringStmt(node.expression as PyStringLiteralExpression)
    }
  }

  private fun annotateDocStringStmt(stmt: PyStringLiteralExpression?) {
    if (stmt == null) return
    if (DocStringParser.getConfiguredDocStringFormat(stmt) == DocStringFormat.REST) {
      val tags = SphinxDocString.ALL_TAGS
      var pos = 0
      while (true) {
        val textRange = DocStringReferenceProvider.findNextTag(stmt.getText(), pos, tags)
        if (textRange == null) break
        holder.newSilentAnnotation(
          HighlightSeverity.INFORMATION).range(textRange.shiftRight(stmt.getTextRange().getStartOffset()))
          .textAttributes(PyHighlighter.PY_DOC_COMMENT_TAG).create()
        pos = textRange.endOffset
      }
    }
  }
}
