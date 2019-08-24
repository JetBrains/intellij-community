package de.plushnikov.intellij.plugin.extension;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.handler.singular.BuilderElementHandler;
import de.plushnikov.intellij.plugin.processor.handler.singular.SingularHandlerFactory;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import lombok.Builder;
import lombok.Singular;
import lombok.experimental.FieldNameConstants;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class LombokRenameFieldReferenceProcessor extends RenameJavaVariableProcessor {

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    if (element instanceof PsiField && !(element instanceof LombokLightFieldBuilder)) {
      final PsiClass containingClass = ((PsiField) element).getContainingClass();
      if (null != containingClass) {
        return Arrays.stream(containingClass.getMethods()).anyMatch(LombokLightMethodBuilder.class::isInstance) ||
          Arrays.stream(containingClass.getInnerClasses()).anyMatch(LombokLightClassBuilder.class::isInstance);
      }
    }
    return false;
  }

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newFieldName, @NotNull Map<PsiElement, String> allRenames) {
    final PsiField psiField = (PsiField) element;
    final PsiClass containingClass = psiField.getContainingClass();
    final String currentFieldName = psiField.getName();
    if (null != containingClass && null != currentFieldName) {
      final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());

      final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);

      final String getterName = LombokUtils.toGetterName(accessorsInfo, currentFieldName, isBoolean);
      final PsiMethod[] psiGetterMethods = containingClass.findMethodsByName(getterName, false);
      for (PsiMethod psiMethod : psiGetterMethods) {
        allRenames.put(psiMethod, LombokUtils.toGetterName(accessorsInfo, newFieldName, isBoolean));
      }

      final String setterName = LombokUtils.toSetterName(accessorsInfo, currentFieldName, isBoolean);
      final PsiMethod[] psiSetterMethods = containingClass.findMethodsByName(setterName, false);
      for (PsiMethod psiMethod : psiSetterMethods) {
        allRenames.put(psiMethod, LombokUtils.toSetterName(accessorsInfo, newFieldName, isBoolean));
      }

      if (!accessorsInfo.isFluent()) {
        final String witherName = LombokUtils.toWitherName(accessorsInfo, currentFieldName, isBoolean);
        final PsiMethod[] psiWitherMethods = containingClass.findMethodsByName(witherName, false);
        for (PsiMethod psiMethod : psiWitherMethods) {
          allRenames.put(psiMethod, LombokUtils.toWitherName(accessorsInfo, newFieldName, isBoolean));
        }
      }

      final PsiAnnotation builderAnnotation = PsiAnnotationSearchUtil.findAnnotation(containingClass, Builder.class, lombok.experimental.SuperBuilder.class);
      if (null != builderAnnotation) {
        final PsiAnnotation singularAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, Singular.class);
        final BuilderElementHandler handler = SingularHandlerFactory.getHandlerFor(psiField, singularAnnotation);
        final List<String> currentBuilderMethodNames = handler.getBuilderMethodNames(accessorsInfo.removePrefix(currentFieldName), singularAnnotation);
        final List<String> newBuilderMethodNames = handler.getBuilderMethodNames(accessorsInfo.removePrefix(newFieldName), singularAnnotation);

        if (currentBuilderMethodNames.size() == newBuilderMethodNames.size()) {
          Arrays.stream(containingClass.getInnerClasses())
            .map(PsiClass::getMethods)
            .flatMap(Arrays::stream)
            .filter(LombokLightMethodBuilder.class::isInstance)
            .filter(psiMethod -> psiMethod.getNavigationElement() == psiField)
            .forEach(psiMethod -> {
              final int methodIndex = currentBuilderMethodNames.indexOf(psiMethod.getName());
              if (methodIndex >= 0) {
                allRenames.put(psiMethod, newBuilderMethodNames.get(methodIndex));
              }
            });
        }
      }

      final boolean hasFieldNameConstantAnnotation = PsiAnnotationSearchUtil.isAnnotatedWith(containingClass, FieldNameConstants.class);
      if (hasFieldNameConstantAnnotation) {
        Arrays.stream(containingClass.getInnerClasses())
          .map(PsiClass::getFields)
          .flatMap(Arrays::stream)
          .filter(LombokLightFieldBuilder.class::isInstance)
          .filter(myField -> myField.getNavigationElement() == psiField)
          .forEach(myField -> allRenames.put(myField, newFieldName));
      }
    }
  }

}
