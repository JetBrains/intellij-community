/**
 * @author Alexey
 */
package com.intellij.lang.properties.editor;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
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
    Map<String, Property> propertyNames = new LinkedHashMap<String, Property>();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      List<Property> properties = propertiesFile.getProperties();
      for (Property property : properties) {
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
        return PropertiesFileType.FILE_ICON;
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
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