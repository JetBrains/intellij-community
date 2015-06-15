/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import java.awt.*;

public class ResourceBundlePropertyStructureViewElement implements StructureViewTreeElement, ResourceBundleEditorViewElement {
  private final ResourceBundle myResourceBundle;
  @NotNull private final PropertiesAnchorizer.PropertyAnchor myAnchor;
  private String myPresentableName;

  private static final TextAttributesKey INCOMPLETE_PROPERTY_KEY;
  private static final TextAttributesKey INCOMPLETE_GROUP_KEY;
  private static final TextAttributesKey GROUP_KEY;

  public static final String PROPERTY_GROUP_KEY_TEXT = "<property>";

  static {
    TextAttributes incompleteKeyTextAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PropertiesHighlighter.PROPERTY_KEY).clone();
    incompleteKeyTextAttributes.setForegroundColor(JBColor.RED);
    INCOMPLETE_PROPERTY_KEY = TextAttributesKey.createTextAttributesKey("INCOMPLETE_PROPERTY_KEY", incompleteKeyTextAttributes);

    TextAttributes groupKeyTextAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PropertiesHighlighter.PROPERTY_KEY).clone();
    groupKeyTextAttributes.setFontType(Font.ITALIC);
    GROUP_KEY = TextAttributesKey.createTextAttributesKey("GROUP_KEY", groupKeyTextAttributes);

    final TextAttributes incompleteGroupKeyTextAttribute = groupKeyTextAttributes.clone();
    incompleteGroupKeyTextAttribute.setForegroundColor(JBColor.RED);
    INCOMPLETE_GROUP_KEY = TextAttributesKey.createTextAttributesKey("INCOMPLETE_GROUP_KEY", incompleteGroupKeyTextAttribute);
  }

  public ResourceBundlePropertyStructureViewElement(final ResourceBundle resourceBundle, final @NotNull PropertiesAnchorizer.PropertyAnchor anchor) {
    myResourceBundle = resourceBundle;
    myAnchor = anchor;
  }

  public IProperty getProperty() {
    return getValue().getRepresentative();
  }

  @Override
  public PsiElement[] getPsiElements() {
    return new PsiElement[] {getProperty().getPsiElement()};
  }

  public void setPresentableName(final String presentableName) {
    myPresentableName = presentableName;
  }

  @Override
  public PropertiesAnchorizer.PropertyAnchor getValue() {
    return myAnchor;
  }

  @Override
  @NotNull
  public StructureViewTreeElement[] getChildren() {
    return EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return new ColoredItemPresentation() {
      @Override
      public String getPresentableText() {
        return myPresentableName == null ? getProperty().getName() : myPresentableName.isEmpty() ? PROPERTY_GROUP_KEY_TEXT : myPresentableName;
      }

      @Override
      public String getLocationString() {
        return null;
      }

      @Override
      public Icon getIcon(boolean open) {
        return PlatformIcons.PROPERTY_ICON;
      }

      @Override
      public TextAttributesKey getTextAttributesKey() {
        if (myPresentableName != null && myPresentableName.isEmpty()) {
          return PropertiesUtil.isPropertyComplete(myResourceBundle, getProperty().getName())
                 ? GROUP_KEY
                 : INCOMPLETE_GROUP_KEY;
        }
        return PropertiesUtil.isPropertyComplete(myResourceBundle, getProperty().getName())
               ? PropertiesHighlighter.PROPERTY_KEY
               : INCOMPLETE_PROPERTY_KEY;
      }
    };
  }

  @Override
  public void navigate(boolean requestFocus) {
    //todo
  }

  @Override
  public boolean canNavigate() {
    return false;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }
}
