package com.intellij.lang.properties;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.DeleteTypeDescriptionLocation;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PropertiesDeleteTypeDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(final PsiElement element, @Nullable final ElementDescriptionLocation location) {
    if (location instanceof DeleteTypeDescriptionLocation && element instanceof Property) {
      int count = ((DeleteTypeDescriptionLocation) location).isPlural() ? 2 : 1;
      return IdeBundle.message("prompt.delete.property", count);
    }
    return null;
  }
}
