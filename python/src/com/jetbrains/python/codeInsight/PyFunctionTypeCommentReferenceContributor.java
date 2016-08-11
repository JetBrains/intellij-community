/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.documentation.docstrings.DocStringTypeReference;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author Mikhail Golubev
 */
public class PyFunctionTypeCommentReferenceContributor extends PsiReferenceContributor {
  public static final PsiElementPattern.Capture<PsiComment> TYPE_COMMENT_PATTERN = psiElement(PsiComment.class)
    .withParent(psiElement(PyStatementList.class).withParent(PyFunction.class))
    .with(new PatternCondition<PsiComment>("isPep484TypeComment") {
      @Override
      public boolean accepts(@NotNull PsiComment comment, ProcessingContext context) {
        final PyFunction func = PsiTreeUtil.getParentOfType(comment, PyFunction.class);
        return func != null && func.getTypeComment() == comment;
      }
    });
  

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(TYPE_COMMENT_PATTERN, new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        final PsiComment comment = (PsiComment)element;
        final String wholeText = comment.getText();
        final String typeText = PyTypingTypeProvider.getTypeCommentValue(wholeText);
        if (typeText != null) {
          final int prefixLength = wholeText.length() - typeText.length();
          final List<PsiReference> references = parseTypeReferences(element, typeText, prefixLength);
          return ArrayUtil.toObjectArray(references, PsiReference.class);
        }
        return PsiReference.EMPTY_ARRAY;
      }
    });
  }
  
  @SuppressWarnings("Duplicates")
  @NotNull
  private static List<PsiReference> parseTypeReferences(@NotNull PsiElement anchor, @NotNull String typeText, int offsetInComment) {
    final List<PsiReference> result = new ArrayList<>();
    final PyTypeParser.ParseResult parseResult = PyTypeParser.parsePep484FunctionTypeComment(anchor, typeText);
    final Map<TextRange, ? extends PyType> types = parseResult.getTypes();
    final Map<? extends PyType, TextRange> fullRanges = parseResult.getFullRanges();
    for (Map.Entry<TextRange, ? extends PyType> pair : types.entrySet()) {
      final PyType t = pair.getValue();
      final TextRange range = pair.getKey().shiftRight(offsetInComment);
      final TextRange fullRange = fullRanges.containsKey(t) ? fullRanges.get(t).shiftRight(offsetInComment) : range;
      final PyImportElement importElement = parseResult.getImports().get(t);
      result.add(new DocStringTypeReference(anchor, range, fullRange, t, importElement));
    }
    return result;
  }
}
