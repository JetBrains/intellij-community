// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * User: catherine
 * Intention to convert between single-quoted and double-quoted strings
 */
public final class PyQuotedStringIntention extends PyBaseIntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.quoted.string");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    PyStringElement stringElement = findConvertibleStringElementUnderCaret(editor, file);
    if (stringElement == null) return false;
    PyStringLiteralExpression stringLiteral = as(stringElement.getParent(), PyStringLiteralExpression.class);
    if (stringLiteral == null) return false;

    final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(stringLiteral, PyDocStringOwner.class);
    if (docStringOwner != null && docStringOwner.getDocStringExpression() == stringLiteral) return false;

    String currentQuote = stringElement.getQuote();
    if (currentQuote.equals("'")) {
      setText(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
    }
    else {
      setText(PyPsiBundle.message("INTN.quoted.string.double.to.single"));
    }
    return true;
  }

  @Nullable
  private static PyStringElement findConvertibleStringElementUnderCaret(@NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement elementUnderCaret = file.findElementAt(editor.getCaretModel().getOffset());
    if (elementUnderCaret == null) return null;
    IElementType elementType = elementUnderCaret.getNode().getElementType();
    if (!(PyTokenTypes.STRING_NODES.contains(elementType) || PyTokenTypes.FSTRING_TOKENS.contains(elementType))) return null;
    PyStringElement stringElement = PsiTreeUtil.getParentOfType(elementUnderCaret, PyStringElement.class, false, PyExpression.class);
    return stringElement != null && PyQuotesUtil.canBeConverted(stringElement, true) ? stringElement : null;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement elementUnderCaret = file.findElementAt(editor.getCaretModel().getOffset());
    PyStringElement stringElement = PsiTreeUtil.getParentOfType(elementUnderCaret, PyStringElement.class, false, PyExpression.class);
    if (stringElement == null) return;
    PyStringLiteralExpression stringLiteral = as(stringElement.getParent(), PyStringLiteralExpression.class);
    if (stringLiteral == null) return;

    String originalQuote = stringElement.getQuote();
    boolean entireLiteralCanBeConverted = ContainerUtil.all(stringLiteral.getStringElements(),
                                                            s -> s.getQuote().equals(originalQuote) && PyQuotesUtil.canBeConverted(s, true));
    if (entireLiteralCanBeConverted) {
      stringLiteral.getStringElements().forEach(PyQuotedStringIntention::convertStringElement);
    }
    else {
      convertStringElement(stringElement);
    }
  }

  private static void convertStringElement(@NotNull PyStringElement stringElement) {
    stringElement.replace(PyQuotesUtil.createCopyWithConvertedQuotes(stringElement));
  }
}
