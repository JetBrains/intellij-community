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

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public abstract class PyBaseConvertCollectionLiteralIntention extends PsiUpdateModCommandAction<PsiElement> {
  private final Class<? extends PySequenceExpression> myTargetCollectionClass;
  private final String myTargetCollectionName;
  private final String myRightBrace;
  private final String myLeftBrace;

  public PyBaseConvertCollectionLiteralIntention(@NotNull Class<? extends PySequenceExpression> targetCollectionClass,
                                                 @NotNull String targetCollectionName,
                                                 @NotNull String leftBrace, @NotNull String rightBrace) {
    super(PsiElement.class);
    myTargetCollectionClass = targetCollectionClass;
    myTargetCollectionName = targetCollectionName;
    myLeftBrace = leftBrace;
    myRightBrace = rightBrace;
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.convert.collection.literal", myTargetCollectionName);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (!(context.file() instanceof PyFile)) {
      return null;
    }
    final PySequenceExpression literal = findCollectionLiteralUnderElement(element);
    if (myTargetCollectionClass.isInstance(literal)) {
      return null;
    }
    if (literal != null && isAvailableForCollection(literal)) {
      if (literal instanceof PyTupleExpression) {
        if (PyTypingTypeProvider.isInsideTypeHint(literal, TypeEvalContext.codeAnalysis(context.project(), context.file()))) return null;
        return Presentation.of(PyPsiBundle.message("INTN.convert.collection.literal", "tuple", myTargetCollectionName));
      }
      else if (literal instanceof PyListLiteralExpression) {
        return Presentation.of(PyPsiBundle.message("INTN.convert.collection.literal", "list", myTargetCollectionName));
      }
      else if (literal instanceof PySetLiteralExpression) {
        return Presentation.of(PyPsiBundle.message("INTN.convert.collection.literal", "set", myTargetCollectionName));
      }
    }
    return null;
  }

  protected boolean isAvailableForCollection(@NotNull PySequenceExpression literal) {
    return true;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PySequenceExpression literal = findCollectionLiteralUnderElement(element);
    assert literal != null;

    final PsiElement replacedElement = wrapCollection(literal);
    final PsiElement copy = prepareOriginalElementCopy(replacedElement.copy());

    final TextRange contentRange = getRangeOfContentWithoutBraces(copy);
    final String contentToWrap = contentRange.substring(copy.getText());
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(context.project());
    final PyExpression newLiteral = elementGenerator.createExpressionFromText(LanguageLevel.forElement(context.file()),
                                                                              myLeftBrace + contentToWrap + myRightBrace);
    replacedElement.replace(newLiteral);
  }

  @NotNull
  protected PsiElement prepareOriginalElementCopy(@NotNull PsiElement copy) {
    final PySequenceExpression sequence = unwrapCollection(copy);
    if (sequence instanceof PyTupleExpression) {
      final PyExpression[] elements = sequence.getElements();
      if (elements.length == 1) {
        final PsiElement next = PyPsiUtils.getNextNonCommentSibling(elements[0], true);
        // Strictly speaking single element tuple must contain trailing comma, but lets check explicitly nonetheless
        if (next != null && next.getNode().getElementType() == PyTokenTypes.COMMA) {
          next.delete();
        }
      }
    }
    return copy;
  }

  @NotNull
  protected static PySequenceExpression unwrapCollection(@NotNull PsiElement literal) {
    final PyParenthesizedExpression parenthesizedExpression = as(literal, PyParenthesizedExpression.class);
    if (parenthesizedExpression != null) {
      final PyExpression containedExpression = parenthesizedExpression.getContainedExpression();
      assert containedExpression != null;
      return (PyTupleExpression)containedExpression;
    }
    return (PySequenceExpression)literal;
  }

  @NotNull
  protected static PsiElement wrapCollection(@NotNull PySequenceExpression literal) {
    if (literal instanceof PyTupleExpression && literal.getParent() instanceof PyParenthesizedExpression) {
      return literal.getParent();
    }
    return literal;
  }

  @NotNull
  private static TextRange getRangeOfContentWithoutBraces(@NotNull PsiElement literal) {
    if (literal instanceof PyTupleExpression) {
      return TextRange.create(0, literal.getTextLength());
    }

    final String replacedText = literal.getText();
    
    final PsiElement firstChild = literal.getFirstChild();
    final int contentStartOffset;
    if (PyTokenTypes.OPEN_BRACES.contains(firstChild.getNode().getElementType())) {
      contentStartOffset = firstChild.getTextLength();
    }
    else {
      contentStartOffset = 0;
    }

    final PsiElement lastChild = literal.getLastChild();
    final int contentEndOffset;
    if (PyTokenTypes.CLOSE_BRACES.contains(lastChild.getNode().getElementType())) {
      contentEndOffset = replacedText.length() - lastChild.getTextLength();
    }
    else {
      contentEndOffset = replacedText.length();
    }

    return TextRange.create(contentStartOffset, contentEndOffset);
  }

  @Nullable
  private static PySequenceExpression findCollectionLiteralUnderElement(PsiElement curElem) {
    final PySequenceExpression seqExpr = PsiTreeUtil.getParentOfType(curElem, PySequenceExpression.class);
    if (seqExpr != null) {
      return seqExpr;
    }
    final PyParenthesizedExpression paren = (PyParenthesizedExpression)PsiTreeUtil.findFirstParent(curElem, element -> {
      final PyParenthesizedExpression parenthesizedExpr = as(element, PyParenthesizedExpression.class);
      return parenthesizedExpr != null && parenthesizedExpr.getContainedExpression() instanceof PyTupleExpression;
    });
    return paren != null ? ((PyTupleExpression)paren.getContainedExpression()) : null;
  }
}
