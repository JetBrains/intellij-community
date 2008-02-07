package com.intellij.refactoring.rename;

import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.Map;
import java.util.List;

public class RenamePropertyProcessor extends RenamePsiElementProcessor {
  public boolean canProcessElement(final PsiElement element) {
    return element instanceof Property;
  }

  public void renameElement(final PsiElement element, final String newName, final UsageInfo[] usages,
                            final RefactoringElementListener listener) throws IncorrectOperationException {
    RenameUtil.doRenameGenericNamedElement(element, newName, usages, listener);
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
