package com.intellij.refactoring.rename;

import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;

import java.util.List;
import java.util.Map;

public class RenamePropertyProcessor extends RenamePsiElementProcessor {
  public boolean canProcessElement(final PsiElement element) {
    return element instanceof Property;
  }

  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    Property property = (Property) element;
    ResourceBundle resourceBundle = property.getContainingFile().getResourceBundle();
    List<Property> properties = PropertiesUtil.findAllProperties(element.getProject(), resourceBundle, property.getUnescapedKey());
    allRenames.clear();
    for (Property otherProperty : properties) {
      allRenames.put(otherProperty, newName);
    }
  }
}
