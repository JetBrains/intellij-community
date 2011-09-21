package de.plushnikov.intellij.lombok.util;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Plushnikov Michail
 */
public class PsiFieldUtil {
  @NotNull
  public static Collection<PsiField> filterFieldsByModifiers(@NotNull PsiField[] psiFields, String... modifiers) {
    Collection<PsiField> filterdFields = new ArrayList<PsiField>(psiFields.length);
    for (PsiField psiField : psiFields) {
      boolean addField = true;

      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        for (String modifier : modifiers) {
          addField &= !modifierList.hasModifierProperty(modifier);
        }
      }

      if (addField) {
        filterdFields.add(psiField);
      }
    }
    return filterdFields;
  }

  @NotNull
  public static Collection<PsiField> filterFieldsByNames(@NotNull PsiField[] psiFields, String... fieldNames) {
    Collection<PsiField> filterdFields = new ArrayList<PsiField>(psiFields.length);
    for (PsiField psiField : psiFields) {
      boolean addField = true;

      final String psiFieldName = psiField.getName();
      for (String fieldName : fieldNames) {
        addField &= !psiFieldName.equals(fieldName);
      }

      if (addField) {
        filterdFields.add(psiField);
      }
    }
    return filterdFields;
  }
}
