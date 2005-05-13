package com.intellij.lang.properties.structureView;

import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.lang.properties.psi.Property;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author cdr
 */
public class PropertiesPrefixGroup implements Group {
  private final Collection<TreeElement> myProperties;
  private final String myPrefix;
  private final String myPresentableName;

  public PropertiesPrefixGroup(final Collection<TreeElement> properties, String prefix, String presentableName) {
    myProperties = properties;
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
        return PropertiesHighlighter.PROPERTY_KEY;
      }
    };
  }

  public Collection<TreeElement> getChildren() {
    Collection<TreeElement> result = new ArrayList<TreeElement>();
    for (TreeElement treeElement : myProperties) {
      if (!(treeElement instanceof PropertiesStructureViewElement)) continue;
      PropertiesStructureViewElement propertiesElement = (PropertiesStructureViewElement)treeElement;
      Property property = propertiesElement.getValue();

      String key = property.getKey();

      if (key == null || key.equals(myPrefix)) {
        continue;
      }
      if (key.startsWith(myPrefix)) {
        result.add(treeElement);
        String presentableName = key.substring(myPrefix.length());
        presentableName = StringUtil.trimStart(presentableName, ".");
        propertiesElement.setPresentableName(presentableName);
      }
    }
    return result;
  }

  public String getPrefix() {
    return myPrefix;
  }
}
