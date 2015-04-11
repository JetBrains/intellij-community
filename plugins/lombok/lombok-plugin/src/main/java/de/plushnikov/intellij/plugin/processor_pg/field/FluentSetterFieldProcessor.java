package de.plushnikov.intellij.plugin.processor_pg.field;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.processor.field.SetterFieldProcessor;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.FluentSetter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

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
  public Collection<String> getAllSetterNames(@NotNull PsiField psiField, boolean isBoolean) {
    return Collections.singletonList(getSetterName(psiField, isBoolean));
  }

  @Override
  protected String getSetterName(@NotNull PsiField psiField, boolean isBoolean) {
    return psiField.getName();
  }

  @Override
  protected PsiType getReturnType(@NotNull PsiField psiField) {
    final PsiClass containingClass = psiField.getContainingClass();
    return null != containingClass ? PsiClassUtil.getTypeWithGenerics(containingClass) : PsiType.VOID;
  }
}
