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
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class PyCompletionConfidence extends CompletionConfidence {

  @NotNull
  @Override
  public ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    ASTNode node = contextElement.getNode();
    if (node != null) {
      if (node.getElementType() == PyTokenTypes.FLOAT_LITERAL) {
        return ThreeState.YES;
      }
      if (PyTokenTypes.STRING_NODES.contains(node.getElementType())) {
        final PsiElement parent = contextElement.getParent();
        if (parent instanceof PyStringLiteralExpression) {
          final List<TextRange> ranges = ((PyStringLiteralExpression)parent).getStringValueTextRanges();
          int relativeOffset = offset - parent.getTextRange().getStartOffset();
          if (ranges.size() > 0 && relativeOffset < ranges.get(0).getStartOffset()) {
            return ThreeState.YES;
          }
        }
      }
    }
    return super.shouldSkipAutopopup(contextElement, psiFile, offset);
  }
}
