package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.FutureFeature.UNICODE_LITERALS;

/**
 * @author Alexey.Ivanov
 */
public class PyByteLiteralInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.byte.literal");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
      PsiFile file = node.getContainingFile(); // can't cache this in the instance, alas
      boolean default_bytes = false;
      if (file instanceof PyFile) {
        PyFile pyfile = (PyFile)file;
        default_bytes = (!UNICODE_LITERALS.requiredAt(pyfile.getLanguageLevel()) &&
                         !pyfile.hasImportFromFuture(UNICODE_LITERALS)
        );
      }
      char first_char = Character.toLowerCase(node.getText().charAt(0));
      if (first_char == 'b' || (default_bytes && first_char != 'u')) {
        String value = node.getStringValue();
        int length = value.length();
        for (int i = 0; i < length; ++i) {
          char c = value.charAt(i);
          if (((int) c) > 255) {
            registerProblem(node, "Byte literal contains characters > 255");
          }
        }
      }
    }
  }
}
