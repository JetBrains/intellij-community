package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.jetbrains.python.psi.PyUtil.as;

public class PyQuotesUtil {
  public static boolean canBeConverted(@NotNull PyStringElement stringElement, boolean checkContainingFString) {
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

  @NotNull
  public static PyStringElement createCopyWithConvertedQuotes(@NotNull PyStringElement element) {
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
