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

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformIcons;

import javax.swing.*;
import java.awt.*;

public class ResourceBundlePropertyStructureViewElement implements StructureViewTreeElement {
  private final String myPropertyName;
  private final Project myProject;
  private final ResourceBundle myResourceBundle;
  private String myPresentableName;

  private static final TextAttributesKey INCOMPLETE_PROPERTY_KEY;

  static {
    TextAttributes textAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PropertiesHighlighter.PROPERTY_KEY).clone();
    textAttributes.setForegroundColor(Color.red);
    INCOMPLETE_PROPERTY_KEY = TextAttributesKey.createTextAttributesKey("INCOMPLETE_PROPERTY_KEY", textAttributes);

  }
  public ResourceBundlePropertyStructureViewElement(final Project project, final ResourceBundle resourceBundle, String propertyName) {
    myProject = project;
    myResourceBundle = resourceBundle;
    myPropertyName = propertyName;
  }

  public void setPresentableName(final String presentableName) {
    myPresentableName = presentableName;
  }

  public String getValue() {
    return myPropertyName;
  }

  public StructureViewTreeElement[] getChildren() {
    return EMPTY_ARRAY;
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return myPresentableName == null ? myPropertyName : myPresentableName;
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return PlatformIcons.PROPERTY_ICON;
      }

      public TextAttributesKey getTextAttributesKey() {
        boolean isComplete = PropertiesUtil.isPropertyComplete(myProject, myResourceBundle, myPropertyName);

        if (isComplete) {
          return PropertiesHighlighter.PROPERTY_KEY;
        }
        return INCOMPLETE_PROPERTY_KEY;
      }
    };
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
