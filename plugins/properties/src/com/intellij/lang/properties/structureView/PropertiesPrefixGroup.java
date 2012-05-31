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
package com.intellij.lang.properties.structureView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.smartTree.Group;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.editor.ResourceBundlePropertyStructureViewElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
public class PropertiesPrefixGroup implements Group {
  private final Collection<TreeElement> myProperties;
  private final @NotNull String myPrefix;
  private final String myPresentableName;
  private final @NotNull String mySeparator;

  public PropertiesPrefixGroup(final Collection<TreeElement> properties, String prefix, String presentableName, final String separator) {
    myProperties = properties;
    myPrefix = prefix;
    myPresentableName = presentableName;
    mySeparator = separator;
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
        return AllIcons.Nodes.Advice;
      }
    };
  }

  public Collection<TreeElement> getChildren() {
    Collection<TreeElement> result = new ArrayList<TreeElement>();
    List<String> prefixWords = StringUtil.split(myPrefix, mySeparator);
    for (TreeElement treeElement : myProperties) {
      String key;
      if (treeElement instanceof PropertiesStructureViewElement) {
        PropertiesStructureViewElement propertiesElement = (PropertiesStructureViewElement)treeElement;
        IProperty property = propertiesElement.getValue();

        key = property.getUnescapedKey();
      }
      else if (treeElement instanceof ResourceBundlePropertyStructureViewElement) {
        key = ((ResourceBundlePropertyStructureViewElement)treeElement).getValue();
      }
      else {
        continue;
      }

      if (key == null || key.equals(myPrefix)) {
        continue;
      }
      List<String> keyWords = StringUtil.split(key, mySeparator);
      boolean startsWith = prefixWords.size() < keyWords.size();
      if (startsWith) {
        for (int i = 0; i < prefixWords.size(); i++) {
          String prefixWord = prefixWords.get(i);
          String keyWord = keyWords.get(i);
          if (!Comparing.strEqual(keyWord, prefixWord)) {
            startsWith = false;
            break;
          }
        }
      }
      if (startsWith) {
        result.add(treeElement);
        String presentableName = key.substring(myPrefix.length());
        presentableName = StringUtil.trimStart(presentableName, mySeparator);
        if (treeElement instanceof PropertiesStructureViewElement) {
          ((PropertiesStructureViewElement)treeElement).setPresentableName(presentableName);
        }
        if (treeElement instanceof ResourceBundlePropertyStructureViewElement) {
          ((ResourceBundlePropertyStructureViewElement)treeElement).setPresentableName(presentableName);
        }
      }
    }
    return result;
  }

  public String getPrefix() {
    return myPrefix;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PropertiesPrefixGroup group = (PropertiesPrefixGroup)o;

    if (!myPrefix.equals(group.myPrefix)) return false;

    return true;
  }

  public int hashCode() {
    return myPrefix.hashCode();
  }
}
