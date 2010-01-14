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

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.properties.psi.Property;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;

import javax.swing.*;

/**
 * @author max
 */
public class PropertiesStructureViewElement implements StructureViewTreeElement {
  private final Property myProperty;
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
