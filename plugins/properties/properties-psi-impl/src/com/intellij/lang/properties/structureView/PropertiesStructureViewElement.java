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
package com.intellij.lang.properties.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.lang.properties.editor.ResourceBundleEditorViewElement;
import com.intellij.lang.properties.editor.ResourceBundlePropertyStructureViewElement;
import com.intellij.lang.properties.psi.Property;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class PropertiesStructureViewElement implements StructureViewTreeElement, ResourceBundleEditorViewElement {
  private final IProperty myProperty;
  private String myPresentableName;

  private static final TextAttributesKey GROUP_KEY;
  static {
    TextAttributes attributes = new TextAttributes();
    attributes.setFontType(Font.ITALIC);
    GROUP_KEY = TextAttributesKey.createTextAttributesKey("STRUCTURE_GROUP_KEY", attributes);
  }


  public PropertiesStructureViewElement(final IProperty element) {
    myProperty = element;
  }

  public IProperty getValue() {
    return myProperty;
  }

  public void navigate(boolean requestFocus) {
    myProperty.navigate(requestFocus);
  }

  public boolean canNavigate() {
    return myProperty.canNavigate();
  }

  public boolean canNavigateToSource() {
    return myProperty.canNavigateToSource();
  }

  @NotNull
  public StructureViewTreeElement[] getChildren() {
    return EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public PsiFile[] getFiles() {
    return null;
  }

  @NotNull
  @Override
  public IProperty[] getProperties() {
    return new IProperty[] {getValue()};
  }

  @NotNull
  public ItemPresentation getPresentation() {
    return new ColoredItemPresentation() {
      @Nullable
      @Override
      public TextAttributesKey getTextAttributesKey() {
        return (myPresentableName != null && myPresentableName.isEmpty()) ? GROUP_KEY :null;
      }

      public String getPresentableText() {
        return myPresentableName == null
               ? myProperty.getUnescapedKey()
               : (myPresentableName.isEmpty() ? ResourceBundlePropertyStructureViewElement.PROPERTY_GROUP_KEY_TEXT : myPresentableName);
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return myProperty.getIcon(0);
      }
    };
  }

  public void setPresentableName(final String presentableName) {
    myPresentableName = presentableName;
  }
}
