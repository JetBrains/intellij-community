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

/**
 * @author Alexey
 */
package com.intellij.lang.properties.editor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResourceBundleFileStructureViewElement implements StructureViewTreeElement {
  private final Project myProject;
  private final ResourceBundle myResourceBundle;

  public ResourceBundleFileStructureViewElement(final Project project, final ResourceBundle resourceBundle) {
    myProject = project;
    myResourceBundle = resourceBundle;
  }

  public ResourceBundle getValue() {
    return myResourceBundle;
  }

  public StructureViewTreeElement[] getChildren() {
    List<PropertiesFile> propertiesFiles = myResourceBundle.getPropertiesFiles(myProject);
    Map<String, IProperty> propertyNames = new LinkedHashMap<String, IProperty>();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      List<IProperty> properties = propertiesFile.getProperties();
      for (IProperty property : properties) {
        String name = property.getUnescapedKey();
        if (!propertyNames.containsKey(name)) {
          propertyNames.put(name, property);
        }
      }
    }
    List<StructureViewTreeElement> result = new ArrayList<StructureViewTreeElement>(propertyNames.size());
    for (String property : propertyNames.keySet()) {
      //result.add(new PropertiesStructureViewElement(property));
      result.add(new ResourceBundlePropertyStructureViewElement(myProject, myResourceBundle, property));
    }
    return result.toArray(new StructureViewTreeElement[result.size()]);
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return myResourceBundle.getBaseName();
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return AllIcons.FileTypes.Properties;
      }
    };
  }

  public void navigate(boolean requestFocus) {

  }

  public boolean canNavigate() {
    return false;
  }

  public boolean canNavigateToSource() {
    return false;
  }
}
