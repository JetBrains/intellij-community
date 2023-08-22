package de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.processor.handler.FieldNameConstantsHandler;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Inspect and validate @FieldNameConstants lombok annotation on a field
 * Creates Inner class containing string constants of the field name for each field of this class
 *
 * @author alanachtenberg
 */
public class FieldNameConstantsProcessor extends AbstractFieldNameConstantsProcessor {

  public FieldNameConstantsProcessor() {
    super(PsiClass.class, LombokClassNames.FIELD_NAME_CONSTANTS);
  }

  @Override
  protected Collection<String> getNamesOfPossibleGeneratedElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    return Collections.singleton(FieldNameConstantsHandler.getTypeName(psiClass, psiAnnotation));
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass,
                                     @NotNull PsiAnnotation psiAnnotation,
                                     @NotNull List<? super PsiElement> target) {
    final Collection<PsiMember> psiMembers = filterMembers(psiClass, psiAnnotation);
    if (!psiMembers.isEmpty()) {
      final String typeName = FieldNameConstantsHandler.getTypeName(psiClass, psiAnnotation);
      Optional<PsiClass> existingClass = PsiClassUtil.getInnerClassInternByName(psiClass, typeName);
      if (existingClass.isEmpty()) {
        LombokLightClassBuilder innerClassOrEnum = FieldNameConstantsHandler.createInnerClassOrEnum(typeName, psiClass, psiAnnotation);
        if (innerClassOrEnum != null) {
          innerClassOrEnum.withFieldSupplier((thisPsiClass) -> FieldNameConstantsHandler.createFields(thisPsiClass, psiMembers));

          target.add(innerClassOrEnum);
        }
      }
    }
  }
}
