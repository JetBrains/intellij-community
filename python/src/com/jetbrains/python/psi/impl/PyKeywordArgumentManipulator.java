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
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyKeywordArgument;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyKeywordArgumentManipulator extends AbstractElementManipulator<PyKeywordArgument> {
  @Override
  public PyKeywordArgument handleContentChange(@NotNull PyKeywordArgument element, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    final ASTNode keywordNode = element.getKeywordNode();
    if (keywordNode != null && keywordNode.getTextRange().shiftRight(-element.getTextRange().getStartOffset()).equals(range)) {
      final LanguageLevel langLevel = LanguageLevel.forElement(element);
      final PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
      final PyCallExpression callExpression = (PyCallExpression) generator.createExpressionFromText(langLevel, "foo(" + newContent + "=None)");
      final PyKeywordArgument kwArg = callExpression.getArgumentList().getKeywordArgument(newContent);
      element.getKeywordNode().getPsi().replace(kwArg.getKeywordNode().getPsi());
      return element;
    }
    throw new IncorrectOperationException("unsupported manipulation on keyword argument");
  }
}
