package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import de.plushnikov.intellij.plugin.processor.handler.singular.BuilderElementHandler;
import de.plushnikov.intellij.plugin.processor.handler.singular.SingularHandlerFactory;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class LombokRenameFieldReferenceProcessor extends RenameJavaVariableProcessor {

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    if (element instanceof PsiField && !(element instanceof LombokLightFieldBuilder)) {
      final PsiClass containingClass = ((PsiField)element).getContainingClass();
      if (null != containingClass) {
        return ContainerUtil.exists(containingClass.getMethods(), LombokLightMethodBuilder.class::isInstance) ||
               ContainerUtil.exists(containingClass.getInnerClasses(), LombokLightClassBuilder.class::isInstance);
      }
    }
    return false;
  }

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newFieldName, @NotNull Map<PsiElement, String> allRenames) {
    final PsiField psiField = (PsiField)element;
    final PsiClass containingClass = psiField.getContainingClass();
    final String currentFieldName = psiField.getName();
    if (null != containingClass) {
      final boolean isBoolean = PsiTypes.booleanType().equals(psiField.getType());

      final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(psiField);

      final String getterName = LombokUtils.toGetterName(accessorsInfo, currentFieldName, isBoolean);
      final String newGetterName = LombokUtils.toGetterName(accessorsInfo, newFieldName, isBoolean);
      if (StringUtil.isNotEmpty(getterName) && StringUtil.isNotEmpty(newGetterName)) {
        final PsiMethod[] psiGetterMethods = containingClass.findMethodsByName(getterName, false);
        for (PsiMethod psiMethod : psiGetterMethods) {
          allRenames.put(psiMethod, newGetterName);
        }
      }

      final String setterName = LombokUtils.toSetterName(accessorsInfo, currentFieldName, isBoolean);
      final String newSetterName = LombokUtils.toSetterName(accessorsInfo, newFieldName, isBoolean);
      if (StringUtil.isNotEmpty(setterName) && StringUtil.isNotEmpty(newSetterName)) {
        final PsiMethod[] psiSetterMethods = containingClass.findMethodsByName(setterName, false);
        for (PsiMethod psiMethod : psiSetterMethods) {
          allRenames.put(psiMethod, newSetterName);
        }
      }

      if (!accessorsInfo.isFluent()) {
        final String witherName = LombokUtils.toWitherName(accessorsInfo, currentFieldName, isBoolean);
        final String newWitherName = LombokUtils.toWitherName(accessorsInfo, newFieldName, isBoolean);
        if (StringUtil.isNotEmpty(witherName) && StringUtil.isNotEmpty(newWitherName)) {
          final PsiMethod[] psiWitherMethods = containingClass.findMethodsByName(witherName, false);
          for (PsiMethod psiMethod : psiWitherMethods) {
            allRenames.put(psiMethod, newWitherName);
          }
        }
      }

      final PsiAnnotation builderAnnotation =
        PsiAnnotationSearchUtil.findAnnotation(containingClass, LombokClassNames.BUILDER, LombokClassNames.SUPER_BUILDER);
      if (null != builderAnnotation) {
        final PsiAnnotation singularAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, LombokClassNames.SINGULAR);
        final BuilderElementHandler handler = SingularHandlerFactory.getHandlerFor(psiField, null != singularAnnotation);

        final String setterPrefix =
          PsiAnnotationUtil.getStringAnnotationValue(builderAnnotation, BuilderHandler.ANNOTATION_SETTER_PREFIX, "");

        final String currentFiledNameWithoutPrefix = accessorsInfo.removePrefixWithDefault(currentFieldName);
        final List<String> currentBuilderMethodNames = handler.getBuilderMethodNames(currentFiledNameWithoutPrefix,
                                                                                     setterPrefix,
                                                                                     singularAnnotation,
                                                                                     accessorsInfo.getCapitalizationStrategy());

        final String newFieldNameWithoutPrefix = accessorsInfo.removePrefixWithDefault(newFieldName);
        final List<String> newBuilderMethodNames = handler.getBuilderMethodNames(newFieldNameWithoutPrefix,
                                                                                 setterPrefix,
                                                                                 singularAnnotation,
                                                                                 accessorsInfo.getCapitalizationStrategy());

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

      final boolean hasFieldNameConstantAnnotation =
        PsiAnnotationSearchUtil.isAnnotatedWith(containingClass, LombokClassNames.FIELD_NAME_CONSTANTS);
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
