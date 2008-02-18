package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class JavaDeleteTypeDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(final PsiElement element, @Nullable final ElementDescriptionLocation location) {
    if (location instanceof DeleteTypeDescriptionLocation && ((DeleteTypeDescriptionLocation) location).isPlural()) {
      if (element instanceof PsiMethod) {
        return IdeBundle.message("prompt.delete.method", 2);
      }
      else if (element instanceof PsiField) {
        return IdeBundle.message("prompt.delete.field", 2);
      }
      else if (element instanceof PsiClass) {
        if (((PsiClass)element).isInterface()) {
          return IdeBundle.message("prompt.delete.interface", 2);
        }
        return element instanceof PsiTypeParameter
               ? IdeBundle.message("prompt.delete.type.parameter", 2)
               : IdeBundle.message("prompt.delete.class", 2);
      }
      else if (element instanceof PsiPackage) {
        return IdeBundle.message("prompt.delete.package", 2); 
      }
    }
    return null;
  }
}
