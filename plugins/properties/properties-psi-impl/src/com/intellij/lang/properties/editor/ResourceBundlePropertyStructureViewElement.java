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
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ResourceBundlePropertyStructureViewElement implements StructureViewTreeElement, ResourceBundleEditorViewElement {
  private final ResourceBundle myResourceBundle;
  private final IProperty myProperty;
  private String myPresentableName;

  private static final TextAttributesKey INCOMPLETE_PROPERTY_KEY;

  static {
    TextAttributes textAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PropertiesHighlighter.PROPERTY_KEY).clone();
    textAttributes.setForegroundColor(Color.red);
    INCOMPLETE_PROPERTY_KEY = TextAttributesKey.createTextAttributesKey("INCOMPLETE_PROPERTY_KEY", textAttributes);

  }

  public ResourceBundlePropertyStructureViewElement(final ResourceBundle resourceBundle, final IProperty property) {
    myResourceBundle = resourceBundle;
    myProperty = property;
  }

  public IProperty getProperty() {
    return myProperty;
  }

  @Override
  public PsiElement[] getPsiElements() {
    return new PsiElement[] {getProperty().getPsiElement()};
  }

  public void setPresentableName(final String presentableName) {
    myPresentableName = presentableName;
  }

  @Override
  public String getValue() {
    return myProperty.getName();
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
        return myPresentableName == null ? myProperty.getName() : myPresentableName;
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
        boolean isComplete = PropertiesUtil.isPropertyComplete(myResourceBundle, myProperty.getName());

        if (isComplete) {
          return PropertiesHighlighter.PROPERTY_KEY;
        }
        return INCOMPLETE_PROPERTY_KEY;
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
