// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * User: catherine
 * Intention to convert between single-quoted and double-quoted strings
 */
public class PyQuotedStringIntention extends PyBaseIntentionAction {
  private SmartPsiElementPointer<PsiElement> myConversionTarget;

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
    boolean allComponentsCanBeConverted = ContainerUtil.all(stringLiteral.getStringElements(),
                                                            s -> s.getQuote().equals(currentQuote) && canBeConverted(s));

    myConversionTarget = SmartPointerManager.createPointer(allComponentsCanBeConverted ? stringLiteral : stringElement);

    if (currentQuote.equals("'")) {
      setText(PyPsiBundle.message("INTN.quoted.string.single.to.double"));
    }
    else {
      setText(PyPsiBundle.message("INTN.quoted.string.double.to.single"));
    }
    return true;
  }

  public @Nullable PyStringElement findConvertibleStringElementUnderCaret(@NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement elemUnderCaret = file.findElementAt(editor.getCaretModel().getOffset());
    PyStringElement stringElement = PsiTreeUtil.getParentOfType(elemUnderCaret, PyStringElement.class, false);
    return stringElement != null && canBeConverted(stringElement) ? stringElement : null;
  }

  public boolean canBeConverted(@NotNull PyStringElement stringElement) {
    return !stringElement.isTripleQuoted() && stringElement.isTerminated() && !stringElement.getContentRange().isEmpty();
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    @Nullable PsiElement target = myConversionTarget.getElement();
    if (target instanceof PyStringLiteralExpression) {
      ((PyStringLiteralExpression)target).getStringElements().forEach(this::convertStringElement);
    }
    else if (target instanceof PyStringElement) {
      convertStringElement((PyStringElement)target);
    }
  }

  public void convertStringElement(@NotNull PyStringElement stringElement) {
    Project project = stringElement.getProject();
    String result = stringElement.getQuote().equals("'")
                    ? convertSingleToDoubleQuoted(stringElement.getText())
                    : convertDoubleToSingleQuoted(stringElement.getText());
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PyStringLiteralExpression st = elementGenerator.createStringLiteralAlreadyEscaped(result);
    stringElement.replace(st.getFirstChild());
  }

  private static String convertDoubleToSingleQuoted(String stringText) {
    StringBuilder stringBuilder = new StringBuilder();

    boolean skipNext = false;
    char[] charArr = stringText.toCharArray();
    for (int i = 0; i != charArr.length; ++i) {
      char ch = charArr[i];
      if (skipNext) {
        skipNext = false;
        continue;
      }
      if (ch == '"') {
        stringBuilder.append('\'');
      }
      else if (ch == '\'') {
        stringBuilder.append("\\'");
      }
      else if (ch == '\\' && charArr[i + 1] == '\"' && !(i + 2 == charArr.length)) {
        skipNext = true;
        stringBuilder.append(charArr[i + 1]);
      }
      else {
        stringBuilder.append(ch);
      }
    }

    return stringBuilder.toString();
  }

  private static String convertSingleToDoubleQuoted(String stringText) {
    StringBuilder stringBuilder = new StringBuilder();
    boolean skipNext = false;
    char[] charArr = stringText.toCharArray();
    for (int i = 0; i != charArr.length; ++i) {
      char ch = charArr[i];
      if (skipNext) {
        skipNext = false;
        continue;
      }
      if (ch == '\'') {
        stringBuilder.append('"');
      }
      else if (ch == '"') {
        stringBuilder.append("\\\"");
      }
      else if (ch == '\\' && charArr[i + 1] == '\'' && !(i + 2 == charArr.length)) {
        skipNext = true;
        stringBuilder.append(charArr[i + 1]);
      }
      else {
        stringBuilder.append(ch);
      }
    }
    return stringBuilder.toString();
  }
}
