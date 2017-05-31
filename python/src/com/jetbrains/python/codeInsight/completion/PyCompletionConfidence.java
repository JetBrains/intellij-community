/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ThreeState;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * @author yole
 */
public class PyCompletionConfidence extends CompletionConfidence {

  @NotNull
  @Override
  public ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    final ASTNode node = contextElement.getNode();
    if (node != null) {
      final IElementType elementType = node.getElementType();
      if (elementType == PyTokenTypes.FLOAT_LITERAL || elementType == PyTokenTypes.INTEGER_LITERAL) {
        return ThreeState.YES;
      }
      if (isSequenceOfDots(contextElement)) {
        return ThreeState.YES;
      }
      if (PyTokenTypes.STRING_NODES.contains(elementType)) {
        final PsiElement parent = contextElement.getParent();
        if (parent instanceof PyStringLiteralExpression) {
          final List<TextRange> ranges = ((PyStringLiteralExpression)parent).getStringValueTextRanges();
          final int relativeOffset = offset - parent.getTextRange().getStartOffset();
          if (ranges.size() > 0 && relativeOffset < ranges.get(0).getStartOffset()) {
            return ThreeState.YES;
          }
        }
      }
    }
    return super.shouldSkipAutopopup(contextElement, psiFile, offset);
  }

  private static boolean isSequenceOfDots(@NotNull PsiElement contextElement) {
    return StreamEx
      .iterate(contextElement, PsiElement::getPrevSibling)
      .takeWhile(Objects::nonNull)
      .limit(3)
      .allMatch(psi -> psi instanceof PsiErrorElement || psi.textMatches("."));
  }
}
