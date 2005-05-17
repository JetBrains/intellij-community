/**
 * @author Alexey
 */
package com.intellij.lang.properties.editor;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.util.Icons;

import javax.swing.*;

public class ResourceBundlePropertyStructureViewElement implements StructureViewTreeElement<String> {
  private final String myPropertyName;
  private final ResourceBundle myResourceBundle;
  private final ItemPresentation myItemPresentation;

  public ResourceBundlePropertyStructureViewElement(final ResourceBundle resourceBundle, String propertyName) {
    myResourceBundle = resourceBundle;
    myPropertyName = propertyName;
    myItemPresentation = new ItemPresentation() {
      public String getPresentableText() {
        return myPropertyName;
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return Icons.PROPERTY_ICON;
      }

      public TextAttributesKey getTextAttributesKey() {
        return PropertiesHighlighter.PROPERTY_KEY;
      }
    };
  }

  public String getValue() {
    return myPropertyName;
  }

  public StructureViewTreeElement[] getChildren() {
    return EMPTY_ARRAY;
  }

  public ItemPresentation getPresentation() {
    return myItemPresentation;
  }

  public void navigate(boolean requestFocus) {
    //todo
  }

  public boolean canNavigate() {
    return false;
  }

  public boolean canNavigateToSource() {
    return false;
  }
}