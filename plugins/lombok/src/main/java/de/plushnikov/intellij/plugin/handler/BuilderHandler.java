package de.plushnikov.intellij.plugin.handler;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import lombok.Builder;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;


public class BuilderHandler {

  public static boolean isDefaultBuilderValue(@NotNull PsiElement highlightedElement) {
    PsiField field = PsiTreeUtil.getParentOfType(highlightedElement, PsiField.class);
    if (field == null) {
      return false;
    }

    return PsiAnnotationSearchUtil.isAnnotatedWith(field, Builder.Default.class.getCanonicalName());
  }
}
