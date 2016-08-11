/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties.refactoring.rename;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.refactoring.PropertiesRefactoringSettings;
import com.intellij.lang.properties.xml.XmlProperty;
import com.intellij.openapi.project.Project;
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

public class RenamePropertyProcessor extends RenamePsiElementProcessor {
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return element instanceof IProperty ||
           (element instanceof PomTargetPsiElement && ((PomTargetPsiElement)element).getTarget() instanceof XmlProperty);
  }

  public void prepareRenaming(final PsiElement element, final String newName,
                              final Map<PsiElement, String> allRenames) {
    ResourceBundle resourceBundle = PropertiesImplUtil.getProperty(element).getPropertiesFile().getResourceBundle();

    final Map<PsiElement, String> allRenamesCopy = new LinkedHashMap<>(allRenames);
    allRenames.clear();
    for (final Map.Entry<PsiElement, String> e : allRenamesCopy.entrySet()) {
      final IProperty property = PropertiesImplUtil.getProperty(e.getKey());
      final List<IProperty> properties = PropertiesUtil.findAllProperties(resourceBundle, property.getUnescapedKey());
      for (final IProperty toRename : properties) {
        allRenames.put(toRename.getPsiElement(), e.getValue());
      }
    }
  }

  @Override
  public void findCollisions(PsiElement element,
                             final String newName,
                             Map<? extends PsiElement, String> allRenames,
                             List<UsageInfo> result) {
    for (final Map.Entry<? extends PsiElement, String> e: allRenames.entrySet()) {
      for (IProperty property : ((PropertiesFile)e.getKey().getContainingFile()).getProperties()) {
        if (Comparing.strEqual(e.getValue(), property.getKey())) {
          result.add(new UnresolvableCollisionUsageInfo(property.getPsiElement(), e.getKey()) {
            @Override
            public String getDescription() {
              return "New property name \'" + e.getValue() + "\' hides existing property";
            }
          });
        }
      }
    }
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
