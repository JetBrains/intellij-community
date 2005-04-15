package com.intellij.lang.properties.structureView;

import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.IconLoader;
import com.intellij.codeInsight.CodeInsightColors;

import javax.swing.*;

/**
 * @author cdr
 */
public class PropertiesPrefixGroup implements Group {
  private final String myPrefix;
  private final String myPresentableName; 

  public PropertiesPrefixGroup(String prefix, String presentableName) {
    myPrefix = prefix;
    myPresentableName = presentableName;
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return myPresentableName;
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return IconLoader.getIcon("/nodes/advice.png");
      }

      public TextAttributesKey getTextAttributesKey() {
        return CodeInsightColors.DEPRECATED_ATTRIBUTES;
      }
    };
  }

  public boolean contains(TreeElement element) {
    if (!(element instanceof PropertiesStructureViewElement)) {
      return false;
    }
    final Property property = ((PropertiesStructureViewElement)element).getValue();
    return property.getKey() != null && property.getKey().startsWith(myPrefix);
  }

  public String getPrefix() {
    return myPrefix;
  }
}
