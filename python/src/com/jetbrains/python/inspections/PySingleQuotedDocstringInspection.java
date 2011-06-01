package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.ConvertDocstringQuickFix;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * Inspection to detect docstrings not using triple double-quoted string
 */
public class PySingleQuotedDocstringInspection extends PyInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.single.quoted.docstring");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {
    String myModificator = "";
    int myLength = 0;
    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyStringLiteralExpression(final PyStringLiteralExpression string) {
      String stringText = string.getText();
      stringText = stringText.substring(getLiteralStartOffset(stringText));
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(string, PyDocStringOwner.class);
      if (docStringOwner != null) {
        if (docStringOwner.getDocStringExpression() == string)  {
          if (!stringText.startsWith("\"\"\"") && !stringText.endsWith("\"\"\"")) {
            ProblemsHolder holder = getHolder();
            if (holder != null) {
              int quoteCount = 1;
              if (stringText.startsWith("'''") && stringText.endsWith("'''")) {
                quoteCount = 3;
              }
              TextRange trStart = new TextRange(myLength, myLength+quoteCount);
              TextRange trEnd = new TextRange(stringText.length()+myLength-quoteCount,
                                              stringText.length()+myLength);
              holder.registerProblem(string, trStart,
                                     PyBundle.message("INSP.message.single.quoted.docstring"), new ConvertDocstringQuickFix(myModificator));
              holder.registerProblem(string, trEnd,
                                     PyBundle.message("INSP.message.single.quoted.docstring"), new ConvertDocstringQuickFix(myModificator));
            }
          }
        }
      }
    }

    private int getLiteralStartOffset(String text) {
      int start = 0;
      char c = Character.toUpperCase(text.charAt(start));
      if (c == 'U' || c == 'B') {
        myModificator += text.charAt(start);
        start++;
        c = Character.toUpperCase(text.charAt(start));
      }
      if (c == 'R') {
        myModificator += text.charAt(start);
        start++;
      }
      myLength = myModificator.length();
      return start;
    }
  }
}
