// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.docstrings;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Internal
public final class DocStringSectionHeaderCompletionContributor extends CompletionContributor implements DumbAware {
  public DocStringSectionHeaderCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().withParent(DocStringTagCompletionContributor.Helper.DOCSTRING_PATTERN),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               final PsiFile file = parameters.getOriginalFile();
               final PsiElement stringNode = parameters.getOriginalPosition();
               assert stringNode != null;
               final int offset = parameters.getOffset();
               final DocStringFormat format = DocStringParser.getConfiguredDocStringFormat(file);
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
