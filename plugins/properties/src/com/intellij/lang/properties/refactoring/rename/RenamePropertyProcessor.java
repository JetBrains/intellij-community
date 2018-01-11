/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.properties.refactoring.rename;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.refactoring.PropertiesRefactoringSettings;
import com.intellij.lang.properties.xml.XmlProperty;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RenamePropertyProcessor extends RenamePsiElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return element instanceof IProperty ||
           (element instanceof PomTargetPsiElement && ((PomTargetPsiElement)element).getTarget() instanceof XmlProperty);
  }

  @Override
  public void prepareRenaming(final PsiElement element, final String newName,
                              final Map<PsiElement, String> allRenames) {
    ResourceBundle resourceBundle = Objects.requireNonNull(PropertiesImplUtil.getProperty(element)).getPropertiesFile().getResourceBundle();

    final Map<PsiElement, String> allRenamesCopy = new LinkedHashMap<>(allRenames);
    allRenames.clear();
    allRenamesCopy.forEach((key, value) -> {
      final IProperty property = PropertiesImplUtil.getProperty(key);
      if (property != null) {
        final List<IProperty> properties = PropertiesUtil.findAllProperties(resourceBundle, property.getUnescapedKey());
        for (final IProperty toRename : properties) {
          allRenames.put(toRename.getPsiElement(), value);
        }
      }
    });
  }

  @Override
  public void findCollisions(PsiElement element,
                             final String newName,
                             Map<? extends PsiElement, String> allRenames,
                             List<UsageInfo> result) {
    allRenames.forEach((key, value) -> {
      for (IProperty property : ((PropertiesFile)key.getContainingFile()).getProperties()) {
        if (Comparing.strEqual(value, property.getKey())) {
          result.add(new UnresolvableCollisionUsageInfo(property.getPsiElement(), key) {
            @Override
            public String getDescription() {
              return "New property name \'" + value + "\' hides existing property";
            }
          });
        }
      }
    });
  }

  @Override
   public boolean isToSearchInComments(PsiElement element) {
     return PropertiesRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS;
   }
 
   @Override
   public void setToSearchInComments(PsiElement element, boolean enabled) {
     PropertiesRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS = enabled;
   }
}
