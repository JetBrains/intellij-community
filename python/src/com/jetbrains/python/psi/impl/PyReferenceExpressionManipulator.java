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
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class PyReferenceExpressionManipulator extends AbstractElementManipulator<PyReferenceExpression> {
  @Override
  public PyReferenceExpression handleContentChange(@NotNull final PyReferenceExpression element, @NotNull final TextRange range, final String newContent)
    throws IncorrectOperationException {
    return null;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement(@NotNull final PyReferenceExpression element) {
    final ASTNode nameElement = element.getNameElement();
    final int startOffset = nameElement != null ? nameElement.getStartOffset() : element.getTextRange().getEndOffset();
    return new TextRange(startOffset - element.getTextOffset(), element.getTextLength());
  }
}
