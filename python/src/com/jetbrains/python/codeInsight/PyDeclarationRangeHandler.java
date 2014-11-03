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
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.hint.DeclarationRangeHandler;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyDeclarationRangeHandler implements DeclarationRangeHandler {
  @NotNull
  @Override
  public TextRange getDeclarationRange(@NotNull PsiElement container) {
    int start = container.getTextRange().getStartOffset();
    if (container instanceof PyFunction) {
      PyParameterList parameterList = ((PyFunction)container).getParameterList();
      return new TextRange(start, parameterList.getTextRange().getEndOffset());
    }
    if (container instanceof PyClass) {
      PyArgumentList argumentList = ((PyClass)container).getSuperClassExpressionList();
      if (argumentList != null) {
        return new TextRange(start, argumentList.getTextRange().getEndOffset());
      }
      ASTNode nameNode = ((PyClass)container).getNameNode();
      if (nameNode != null) {
        return new TextRange(start, nameNode.getStartOffset() + nameNode.getTextLength());
      }
    }
    return container.getTextRange();
  }
}
