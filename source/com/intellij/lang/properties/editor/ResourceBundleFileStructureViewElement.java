/**
 * @author Alexey
 */
package com.intellij.lang.properties.editor;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.structureView.PropertiesStructureViewElement;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;

import javax.swing.*;
import java.util.*;

public class ResourceBundleFileStructureViewElement implements StructureViewTreeElement<ResourceBundle> {
  private final ResourceBundle myResourceBundle;

  public ResourceBundleFileStructureViewElement(final ResourceBundle resourceBundle) {
    myResourceBundle = resourceBundle;
  }

  public ResourceBundle getValue() {
    return myResourceBundle;
  }

  public StructureViewTreeElement[] getChildren() {
    List<PropertiesFile> propertiesFiles = myResourceBundle.getPropertiesFiles();
    Map<String, Property> propertyNames = new LinkedHashMap<String, Property>();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      List<Property> properties = propertiesFile.getProperties();
      for (Property property : properties) {
        String name = property.getKey();
        if (!propertyNames.containsKey(name)) {
          propertyNames.put(name, property);
        }
      }
    }
    List<StructureViewTreeElement> result = new ArrayList<StructureViewTreeElement>(propertyNames.size());
    for (Property property : propertyNames.values()) {
      result.add(new PropertiesStructureViewElement(property));
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