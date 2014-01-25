package de.plushnikov.intellij.plugin.util;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

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
  public static Collection<PsiField> filterFieldsByNames(@NotNull PsiField[] psiFields, @NotNull Collection<String> excludeFieldNames) {
    Collection<PsiField> filteredFields = new ArrayList<PsiField>(psiFields.length);
    for (PsiField psiField : psiFields) {
      if (!excludeFieldNames.contains(psiField.getName())) {
        filteredFields.add(psiField);
      }
    }
    return filteredFields;
  }

  @NotNull
  public static Collection<PsiField> filterFieldsByNames(@NotNull PsiField[] psiFields, String... fieldNames) {
    return filterFieldsByNames(psiFields, new HashSet<String>(Arrays.asList(fieldNames)));
  }
}
