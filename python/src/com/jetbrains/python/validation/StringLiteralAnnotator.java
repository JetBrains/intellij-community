package com.jetbrains.python.validation;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Marks string literals as byte or Unicode.
 * <br/>
 * User: dcheryasov
 */
public class StringLiteralAnnotator extends PyAnnotator {

  private LanguageLevel myLanguageLevel = null;
  private Boolean myUnicodeImported = null;

  private boolean isDefaultUnicode(@NotNull PsiElement node) {
    boolean ret;
    if (myLanguageLevel == null) {
      myLanguageLevel = LanguageLevel.forElement(node);
    }
    ret = myLanguageLevel.isAtLeast(LanguageLevel.PYTHON30);
    if (myUnicodeImported == null) {
      final PsiFile file = node.getContainingFile();
      if (file instanceof PyFile) {
        myUnicodeImported = ((PyFile)file).hasImportFromFuture(FutureFeature.UNICODE_LITERALS);
      }
    }
    if (myUnicodeImported != null) ret |= myUnicodeImported;
    return ret;
  }

  @Override
  public void visitPyFile(PyFile node) {
    myLanguageLevel = null;
    myUnicodeImported = null;
  }

  @Override
  public void visitPyStringLiteralExpression(PyStringLiteralExpression expr) {
    List<ASTNode> literal_nodes = expr.getStringNodes();
    for (ASTNode node : literal_nodes) {
      CharSequence chars = node.getChars();
      if (chars.length() > 0) {
        char first_char = Character.toLowerCase(chars.charAt(0));
        boolean is_unicode = isDefaultUnicode(expr);
        is_unicode |= (first_char == 'u');
        is_unicode &= (first_char != 'b');
        if (is_unicode) getHolder().createInfoAnnotation(node, null).setTextAttributes(PyHighlighter.PY_DECORATOR); // XXX
      }
    }
  }
}
