/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.documentation.docstrings;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author Mikhail Golubev
 */
public class DocStringSectionHeaderCompletionContributor extends CompletionContributor {
  public DocStringSectionHeaderCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().withParent(DocStringTagCompletionContributor.DOCSTRING_PATTERN),
           new CompletionProvider<CompletionParameters>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               final PsiFile file = parameters.getOriginalFile();
               final PsiElement stringNode = parameters.getOriginalPosition();
               assert stringNode != null;
               final int offset = parameters.getOffset();
               final DocStringFormat format = DocStringUtil.getConfiguredDocStringFormat(file);
               if (!(format == DocStringFormat.GOOGLE || format == DocStringFormat.NUMPY)) {
                 return;
               }
               // Numpy docstring format is ambiguous. Because parameters have the same indentation as section headers,
               // beginning of section header can be parsed as parameter reference
               if (format == DocStringFormat.GOOGLE && file.findReferenceAt(offset) != null) {
                 return;
               }
               final Document document = parameters.getEditor().getDocument();
               final TextRange linePrefixRange = new TextRange(document.getLineStartOffset(document.getLineNumber(offset)), offset);
               final String prefix = StringUtil.trimLeading(document.getText(linePrefixRange));
               result = result.withPrefixMatcher(prefix).caseInsensitive();
               final Iterable<String> names = format == DocStringFormat.GOOGLE ? GoogleCodeStyleDocString.PREFERRED_SECTION_HEADERS
                                                                               : NumpyDocString.PREFERRED_SECTION_HEADERS; 
               for (String tag : names) {
                 result.addElement(LookupElementBuilder.create(tag));
               }
             }
           });
  }
}
