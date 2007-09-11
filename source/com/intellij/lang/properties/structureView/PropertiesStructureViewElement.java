package com.intellij.lang.properties.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.properties.psi.Property;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 10, 2005
 * Time: 3:26:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesStructureViewElement implements StructureViewTreeElement {
  private Property myProperty;
  private String myPresentableName;

  public PropertiesStructureViewElement(final Property element) {
    myProperty = element;
  }

  public Property getValue() {
    return myProperty;
  }

  public void navigate(boolean requestFocus) {
    ((NavigationItem)myProperty).navigate(requestFocus);
  }

  public boolean canNavigate() {
    return ((NavigationItem)myProperty).canNavigate();
  }

  public boolean canNavigateToSource() {
    return ((NavigationItem)myProperty).canNavigateToSource();
  }

  public StructureViewTreeElement[] getChildren() {
    return EMPTY_ARRAY;
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        if (myPresentableName == null) {
          return myProperty.getUnescapedKey();
        }
        else {
          return myPresentableName;
        }
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return myProperty.getIcon(Iconable.ICON_FLAG_OPEN);
      }
    };
  }

  public void setPresentableName(final String presentableName) {
    myPresentableName = presentableName;
  }
}
