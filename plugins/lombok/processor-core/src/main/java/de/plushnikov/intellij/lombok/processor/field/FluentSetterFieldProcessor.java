package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import lombok.FluentSetter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Inspect and validate @FluentSetter lombok-pg annotation on a field
 * Creates fluent setter method for this field
 *
 * @author Plushnikov Michail
 */
public class FluentSetterFieldProcessor extends SetterFieldProcessor {

  public FluentSetterFieldProcessor() {
    super(FluentSetter.class, PsiMethod.class);
  }

  @Override
  public List<String> getAllSetterNames(@NotNull PsiField psiField, boolean isBoolean) {
    return Arrays.asList(getSetterName(psiField, isBoolean));
  }

  @Override
  protected String getSetterName(@NotNull PsiField psiField, boolean isBoolean) {
    return psiField.getName();
  }

  @Override
  protected PsiType getReturnType(@NotNull PsiField psiField) {
    final PsiClass containingClass = psiField.getContainingClass();
    return null != containingClass ? PsiTypesUtil.getClassType(containingClass) : PsiType.NULL;
  }
}
