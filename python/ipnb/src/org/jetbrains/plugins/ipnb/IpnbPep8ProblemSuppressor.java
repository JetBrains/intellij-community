package org.jetbrains.plugins.ipnb;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.validation.Pep8ExternalAnnotator;
import com.jetbrains.python.validation.Pep8ProblemSuppressor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.psi.IpnbPyFragment;

/**
 * @author Mikhail Golubev
 */
public class IpnbPep8ProblemSuppressor implements Pep8ProblemSuppressor {
  @Override
  public boolean isProblemSuppressed(@NotNull Pep8ExternalAnnotator.Problem problem,
                                     @NotNull PsiFile file,
                                     @Nullable PsiElement targetElement) {
    // Ignore warnings about missing new line at the end of file inside IPython notebook cells
    return file instanceof IpnbPyFragment && problem.getCode().equals("W292");
  }
}
