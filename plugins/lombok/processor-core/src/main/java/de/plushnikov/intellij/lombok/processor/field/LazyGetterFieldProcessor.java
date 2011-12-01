package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import lombok.LazyGetter;
import org.jetbrains.annotations.NotNull;

/**
 * Inspect and validate @LazyGetter lombok-pg annotation on a field
 * Creates lazy getter method for this field
 *
 * @author Plushnikov Michail
 */
public class LazyGetterFieldProcessor extends GetterFieldProcessor {

  public LazyGetterFieldProcessor() {
    super(LazyGetter.class, PsiMethod.class);
  }

  protected boolean isLazyGetter(@NotNull PsiAnnotation psiAnnotation) {
    return true;
  }
}
