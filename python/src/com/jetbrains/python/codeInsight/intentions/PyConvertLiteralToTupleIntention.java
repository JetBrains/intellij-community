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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class PyConvertLiteralToTupleIntention extends PyBaseConvertCollectionLiteralIntention {
  public PyConvertLiteralToTupleIntention() {
    super(PyTupleExpression.class, "tuple", "(", ")");
  }


  @NotNull
  @Override
  protected String prepareContent(@NotNull PsiElement replacedElement, 
                                  @NotNull PySequenceExpression collection, 
                                  @NotNull TextRange contentRange) {
    assert !(collection instanceof PyTupleExpression);

    final String contentWithoutBraces = super.prepareContent(replacedElement, collection, contentRange);
    
    final PyExpression[] elements = collection.getElements();
    if (elements.length != 1) {
      return contentWithoutBraces;
    }
    
    final PsiElement lastChild = collection.getLastChild();
    boolean endsWithComma = false;
    final IElementType lastChildType = lastChild.getNode().getElementType();
    if (lastChildType == PyTokenTypes.COMMA) {
      endsWithComma = true;
    }
    else if (PyTokenTypes.CLOSE_BRACES.contains(lastChildType)) {
      final PsiElement prev = PyPsiUtils.getPrevNonWhitespaceSibling(lastChild);
      if (prev != null && prev.getNode().getElementType() == PyTokenTypes.COMMA) {
        endsWithComma = true;
      }
    }
    if (endsWithComma) {
      return contentWithoutBraces;
    }

    final PyExpression singleElem = elements[0];
    final int commaOffset = singleElem.getTextRange().getEndOffset() - replacedElement.getTextRange().getStartOffset();

    final String wholeText = replacedElement.getText();
    return wholeText.substring(contentRange.getStartOffset(), commaOffset) + 
           "," + 
           wholeText.substring(commaOffset, contentRange.getEndOffset());
  }
}
