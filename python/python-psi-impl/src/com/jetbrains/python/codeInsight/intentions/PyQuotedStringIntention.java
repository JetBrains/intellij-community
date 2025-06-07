// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
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
public final class PyQuotedStringIntention extends PsiUpdateModCommandAction<PsiElement> {
  public PyQuotedStringIntention() {
    super(PsiElement.class);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (!(context.file() instanceof PyFile)) {
      return null;
    }

    PyStringElement stringElement = findConvertibleStringElementUnderCaret(element);
    if (stringElement == null) return null;
    PyStringLiteralExpression stringLiteral = as(stringElement.getParent(), PyStringLiteralExpression.class);
    if (stringLiteral == null) return null;

    final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(stringLiteral, PyDocStringOwner.class);
    if (docStringOwner != null && docStringOwner.getDocStringExpression() == stringLiteral) return null;

    String currentQuote = stringElement.getQuote();
    if (currentQuote.equals("'")) {
      return Presentation.of(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
    }
    else {
      return Presentation.of(PyPsiBundle.message("INTN.quoted.string.double.to.single"));
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.quoted.string");
  }

  private static @Nullable PyStringElement findConvertibleStringElementUnderCaret(@NotNull PsiElement element) {
    IElementType elementType = element.getNode().getElementType();
    if (!(PyTokenTypes.STRING_NODES.contains(elementType) || PyTokenTypes.FSTRING_TOKENS.contains(elementType))) return null;
    PyStringElement stringElement = PsiTreeUtil.getParentOfType(element, PyStringElement.class, false, PyExpression.class);
    return stringElement != null && PyQuotesUtil.canBeConverted(stringElement, true) ? stringElement : null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PyStringElement stringElement = PsiTreeUtil.getParentOfType(element, PyStringElement.class, false, PyExpression.class);
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

  public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, @NotNull PsiFile file) {
    Presentation presentation = getPresentation(ActionContext.from(editor, file));
    return presentation != null;
  }

  public void invoke(@NotNull Project project, @Nullable Editor editor, @NotNull PsiFile file) {
    ActionContext context = ActionContext.from(editor, file);
    ModCommandExecutor.executeInteractively(context, getFamilyName(), editor, () -> perform(context));
  }

  private static void convertStringElement(@NotNull PyStringElement stringElement) {
    stringElement.replace(PyQuotesUtil.createCopyWithConvertedQuotes(stringElement));
  }
}
