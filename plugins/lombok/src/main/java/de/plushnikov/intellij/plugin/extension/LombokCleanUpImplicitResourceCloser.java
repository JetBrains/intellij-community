package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInspection.resources.ImplicitResourceCloser;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import lombok.Cleanup;
import org.jetbrains.annotations.NotNull;

/**
 * Implement additional way to close AutoCloseables by @lombok.Cleanup for IntelliJ
 */
public class LombokCleanUpImplicitResourceCloser implements ImplicitResourceCloser {
  @Override
  public boolean isSafelyClosed(@NotNull PsiVariable variable) {
    return PsiAnnotationSearchUtil.isAnnotatedWith(variable, Cleanup.class);
  }
}
