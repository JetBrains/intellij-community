package org.jetbrains.plugins.ipnb;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyEmptyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.validation.Pep8ExternalAnnotator;
import com.jetbrains.python.validation.Pep8ProblemSuppressor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.psi.IpnbPyFragment;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class IpnbPep8ProblemSuppressor implements Pep8ProblemSuppressor {
  @Override
  public boolean isProblemSuppressed(@NotNull Pep8ExternalAnnotator.Problem problem,
                                     @NotNull PsiFile file,
                                     @Nullable PsiElement targetElement) {
    if (file instanceof IpnbPyFragment) {
      // Ignore warnings about missing new line at the end of file
      if (problem.getCode().equals("W292")) {
        return true;
      }

      // Ignore warnings about imports not at the top of file if there are magic commands before
      if (problem.getCode().equals("E402") && targetElement != null) {
        final PyImportStatement importStatement = PsiTreeUtil.getParentOfType(targetElement, PyImportStatement.class);
        if (importStatement != null && importStatement.getParent() == file) {
          boolean containsMagicCommandsBefore = false;
          PsiElement prev = PyPsiUtils.getPrevNonWhitespaceSibling(importStatement);
          while (prev != null) {
            if (prev instanceof PyEmptyExpression && prev.getText().startsWith("%")) {
              containsMagicCommandsBefore = true;
            }
            else if (!isModuleLevelDocstring(prev, (PyFile)file)) {
              return false;
            }
            prev = PyPsiUtils.getPrevNonWhitespaceSibling(prev);
          }
          return containsMagicCommandsBefore;
        }
      }
    }
    return false;
  }

  private static boolean isModuleLevelDocstring(@NotNull PsiElement element, @NotNull PyFile file) {
    final PyExpressionStatement expressionStatement = as(element, PyExpressionStatement.class);
    return expressionStatement != null && expressionStatement.getExpression() == file.getDocStringExpression();
  }
}
