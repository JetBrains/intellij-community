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

import java.util.Collection;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * User: catherine
 * Intention to convert between single-quoted and double-quoted strings
 */
public class PyQuotedStringIntention extends PyBaseIntentionAction {
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
    return stringElement != null && canBeConverted(stringElement, true) ? stringElement : null;
  }

  private static boolean canBeConverted(@NotNull PyStringElement stringElement, boolean checkContainingFString) {
    if (stringElement.isTripleQuoted() || !stringElement.isTerminated()) return false;
    if (checkContainingFString) {
      PyFormattedStringElement parentFString = PsiTreeUtil.getParentOfType(stringElement, PyFormattedStringElement.class, true,
                                                                           PyStatement.class);
      char targetQuote = PyStringLiteralUtil.flipQuote(stringElement.getQuote().charAt(0));
      if (parentFString != null) {
        boolean parentFStringUsesTargetQuotes = parentFString.getQuote().equals(Character.toString(targetQuote));
        if (parentFStringUsesTargetQuotes) return false;
        boolean conversionIntroducesBackslashEscapedQuote = stringElement.textContains(targetQuote);
        if (conversionIntroducesBackslashEscapedQuote) return false;
      }
    }
    PyFormattedStringElement fStringElement = as(stringElement, PyFormattedStringElement.class);
    if (fStringElement != null) {
      Collection<PyStringElement> innerStrings = PsiTreeUtil.findChildrenOfType(fStringElement, PyStringElement.class);
      if (ContainerUtil.exists(innerStrings, s -> !canBeConverted(s, false))) {
        return false;
      }
    }
    return true;
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
                                                            s -> s.getQuote().equals(originalQuote) && canBeConverted(s, true));
    if (entireLiteralCanBeConverted) {
      stringLiteral.getStringElements().forEach(PyQuotedStringIntention::convertStringElement);
    }
    else {
      convertStringElement(stringElement);
    }
  }

  private static void convertStringElement(@NotNull PyStringElement stringElement) {
    stringElement.replace(createCopyWithConvertedQuotes(stringElement));
  }

  @NotNull
  private static PyStringElement createCopyWithConvertedQuotes(@NotNull PyStringElement element) {
    StringBuilder builder = new StringBuilder();
    processStringElement(builder, element);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
    return (PyStringElement)elementGenerator.createStringLiteralAlreadyEscaped(builder.toString()).getFirstChild();
  }

  private static void processStringElement(@NotNull StringBuilder builder, @NotNull PyStringElement stringElement) {
    char originalQuote = stringElement.getQuote().charAt(0);
    if (stringElement instanceof PyPlainStringElement) {
      processStringElementText(builder, stringElement.getText(), originalQuote);
    }
    else {
      stringElement.acceptChildren(new PyRecursiveElementVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          if (element instanceof PyStringElement) {
            processStringElement(builder, (PyStringElement)element);
          }
          else if (PyTokenTypes.FSTRING_TOKENS.contains(element.getNode().getElementType())) {
            processStringElementText(builder, element.getText(), originalQuote);
          }
          else if (element.getNode().getChildren(null).length == 0) {
            builder.append(element.getText());
          }
          else {
            super.visitElement(element);
          }
        }
      });
    }
  }

  private static void processStringElementText(@NotNull StringBuilder builder, @NotNull String stringText, char originalQuote) {
    char targetQuote = PyStringLiteralUtil.flipQuote(originalQuote);
    char[] charArr = stringText.toCharArray();
    int i = 0;
    while (i != charArr.length) {
      char ch1 = charArr[i];
      char ch2 = i + 1 < charArr.length ? charArr[i + 1] : '\0';
      if (ch1 == originalQuote) {
        builder.append(targetQuote);
      }
      else if (ch1 == targetQuote) {
        builder.append("\\").append(targetQuote);
      }
      else if (ch1 == '\\') {
        if (ch2 == originalQuote) {
          builder.append(ch2);
          i++;
        }
        else if (ch2 == '\0') {
          builder.append(ch1);
        }
        else {
          builder.append(ch1).append(ch2);
          i++;
        }
      }
      else {
        builder.append(ch1);
      }
      i++;
    }
  }
}
