/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
