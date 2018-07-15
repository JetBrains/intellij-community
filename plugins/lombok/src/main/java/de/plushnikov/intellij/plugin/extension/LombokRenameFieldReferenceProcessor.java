package de.plushnikov.intellij.plugin.extension;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;

public class LombokRenameFieldReferenceProcessor extends RenameJavaVariableProcessor {

  public LombokRenameFieldReferenceProcessor() {
  }

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    if (element instanceof PsiField) {
      final PsiField psiField = (PsiField) element;
      final PsiClass containingClass = psiField.getContainingClass();
      if (null != containingClass) {
        return Arrays.stream(containingClass.getAllMethods())
          .filter(LombokLightMethodBuilder.class::isInstance)
          .anyMatch(psiMethod -> psiMethod.getNavigationElement() == psiField);
      }
    }
    return false;
  }

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames) {
    final PsiField psiField = (PsiField) element;
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);

      final String currentFieldName = psiField.getName();
      final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());

      final String getterName = LombokUtils.toGetterName(accessorsInfo, currentFieldName, isBoolean);
      final String setterName = LombokUtils.toSetterName(accessorsInfo, currentFieldName, isBoolean);

      final PsiMethod[] psiGetterMethods = containingClass.findMethodsByName(getterName, false);
      final PsiMethod[] psiSetterMethods = containingClass.findMethodsByName(setterName, false);

      String newFieldName = accessorsInfo.removePrefix(newName);
      for (PsiMethod psiMethod : psiGetterMethods) {
        allRenames.put(psiMethod, LombokUtils.toGetterName(accessorsInfo, newFieldName, isBoolean));
      }

      for (PsiMethod psiMethod : psiSetterMethods) {
        allRenames.put(psiMethod, LombokUtils.toSetterName(accessorsInfo, newFieldName, isBoolean));
      }
    }
  }
}
