package com.jetbrains.python.editor;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;

/**
 * User : catherine
 */
public class PythonDocCommentUtil {

  static public boolean inDocComment(PsiElement element) {
    PyStringLiteralExpression string = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
    if (string != null) {
      PyElement func = PsiTreeUtil.getParentOfType(element, PyFunction.class, PyClass.class, PyFile.class);
      if (func != null) {
        final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(element,
                                                                            PyDocStringOwner.class);
        if (docStringOwner == func) {
          PyStringLiteralExpression str = docStringOwner.getDocStringExpression();
          String text = element.getText();
          if (str != null && text.equals(str.getText())) {
            PsiErrorElement error = PsiTreeUtil.getNextSiblingOfType(string, PsiErrorElement.class);
            if (error != null)
              return true;
            error = PsiTreeUtil.getNextSiblingOfType(string.getParent(), PsiErrorElement.class);
            if (error != null)
              return true;

            if (text.length() > 3 && (text.length() < 6 || (!text.endsWith("\"\"\"") && !text.endsWith("'''"))))
              return true;
          }
        }
      }
    }
    return false;
  }

  static public String generateDocForClass(PsiElement klass, String suffix) {
    String ws = "\n";
    if (klass instanceof PyClass) {
      PsiWhiteSpace whitespace = PsiTreeUtil.getPrevSiblingOfType(((PyClass)klass).getStatementList(), PsiWhiteSpace.class);
      if (whitespace != null) {
        String[] spaces = whitespace.getText().split("\n");
        if (spaces.length > 1)
          ws = ws + whitespace.getText().split("\n")[1];
      }
    }
    return ws+suffix;
  }
}
