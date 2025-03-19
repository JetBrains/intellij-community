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
package com.jetbrains.python.documentation.docstrings

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyDocStringOwner
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyStringLiteralExpression

class DocStringTagCompletionContributor : CompletionContributor(), DumbAware {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(DOCSTRING_PATTERN),
           object : CompletionProvider<CompletionParameters?>() {
             override fun addCompletions(
               parameters: CompletionParameters,
               context: ProcessingContext,
               result: CompletionResultSet,
             ) {
               val file = parameters.originalFile
               if (DocStringParser.getConfiguredDocStringFormat(file) == DocStringFormat.REST) {
                 var offset = parameters.offset
                 val text = file.getText()
                 val prefix = ':'
                 if (offset > 0) {
                   offset--
                 }
                 val prefixBuilder = StringBuilder()
                 while (offset > 0 && (Character.isLetterOrDigit(text[offset]) || text[offset] == prefix)) {
                   prefixBuilder.insert(0, text[offset])
                   if (text[offset] == prefix) {
                     offset--
                     break
                   }
                   offset--
                 }
                 while (offset > 0) {
                   offset--
                   if (text[offset] == '\n' || text[offset] == '\"' || text[offset] == '\'') {
                     break
                   }
                   if (!Character.isWhitespace(text[offset])) {
                     return
                   }
                 }
                 var resultSet = result
                 if (!prefixBuilder.isEmpty()) {
                   resultSet = resultSet.withPrefixMatcher(prefixBuilder.toString())
                 }
                 for (tag in SphinxDocString.ALL_TAGS) {
                   resultSet.addElement(LookupElementBuilder.create(tag))
                 }
               }
             }
           })
  }

  companion object {
    @JvmField
    val DOCSTRING_PATTERN: PsiElementPattern.Capture<PyStringLiteralExpression?> =
      PlatformPatterns.psiElement<PyStringLiteralExpression?>(PyStringLiteralExpression::class.java)
        .withParent(
          PlatformPatterns
            .psiElement<PyExpressionStatement?>(PyExpressionStatement::class.java)
            .inside(PyDocStringOwner::class.java))
  }
}
