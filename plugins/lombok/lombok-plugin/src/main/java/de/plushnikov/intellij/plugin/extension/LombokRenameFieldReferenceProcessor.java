package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.field.SetterFieldProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class LombokRenameFieldReferenceProcessor extends RenameJavaVariableProcessor {

  private final SetterFieldProcessor setterFieldProcessor;

  public LombokRenameFieldReferenceProcessor() {
    setterFieldProcessor = new SetterFieldProcessor();
  }

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    final boolean isPsiJavaField = element instanceof PsiField && StdFileTypes.JAVA.getLanguage().equals(element.getLanguage());
    if (isPsiJavaField) {
      final PsiField psiField = (PsiField) element;
      AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);
      final String accessorFieldName = accessorsInfo.removePrefix(psiField.getName());
      if (!psiField.getName().equals(accessorFieldName)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames) {
    final PsiField psiField = (PsiField) element;
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);
      final String psiFieldName = psiField.getName();
      final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());

      final String getterName = setterFieldProcessor.getGetterName(accessorsInfo, psiFieldName, isBoolean, containingClass);
      final String setterName = setterFieldProcessor.getSetterName(accessorsInfo, psiFieldName, isBoolean);

      final PsiMethod[] psiGetterMethods = containingClass.findMethodsByName(getterName, false);
      final PsiMethod[] psiSetterMethods = containingClass.findMethodsByName(setterName, false);

      for (PsiMethod psiMethod : psiGetterMethods) {
        allRenames.put(psiMethod, setterFieldProcessor.getGetterName(accessorsInfo, newName, isBoolean, containingClass));
      }

      for (PsiMethod psiMethod : psiSetterMethods) {
        allRenames.put(psiMethod, setterFieldProcessor.getSetterName(accessorsInfo, newName, isBoolean));
      }
    }
  }
}
