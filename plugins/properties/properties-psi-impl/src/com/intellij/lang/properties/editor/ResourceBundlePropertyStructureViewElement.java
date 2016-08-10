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

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.inspections.InspectedPropertyNodeInfo;
import com.intellij.lang.properties.editor.inspections.ResourceBundleEditorProblemDescriptor;
import com.intellij.lang.properties.editor.inspections.ResourceBundleEditorRenderer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ResourceBundlePropertyStructureViewElement implements StructureViewTreeElement, ResourceBundleEditorViewElement {
  private static final TextAttributesKey GROUP_KEY;

  public static final String PROPERTY_GROUP_KEY_TEXT = "<property>";

  private final @NotNull PropertiesAnchorizer.PropertyAnchor myAnchor;
  private String myPresentableName;


  static {
    TextAttributes groupKeyTextAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PropertiesHighlighter.PROPERTY_KEY).clone();
    groupKeyTextAttributes.setFontType(Font.ITALIC);
    GROUP_KEY = TextAttributesKey.createTextAttributesKey("GROUP_KEY", groupKeyTextAttributes);
  }

  private volatile InspectedPropertyNodeInfo myInspectedPropertyNodeInfo;

  public ResourceBundlePropertyStructureViewElement(final ResourceBundle resourceBundle, final @NotNull PropertiesAnchorizer.PropertyAnchor anchor) {
    myAnchor = anchor;
  }

  public IProperty getProperty() {
    return getValue().getRepresentative();
  }

  @NotNull
  @Override
  public IProperty[] getProperties() {
    return new IProperty[] {getProperty()};
  }

  @Nullable
  @Override
  public PsiFile[] getFiles() {
    return null;
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

  @NotNull
  public Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>[] getProblemDescriptors() {
    return myInspectedPropertyNodeInfo == null ? new Pair[0] : myInspectedPropertyNodeInfo.getDescriptors();
  }

  public void setInspectedPropertyNodeInfo(InspectedPropertyNodeInfo inspectedPropertyNodeInfo) {
    myInspectedPropertyNodeInfo = inspectedPropertyNodeInfo;
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return new ResourceBundleEditorRenderer.TextAttributesPresentation() {

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
      public TextAttributes getTextAttributes(EditorColorsScheme colorsScheme) {
        final TextAttributesKey baseAttrKey =
          (myPresentableName != null && myPresentableName.isEmpty()) ? GROUP_KEY : PropertiesHighlighter.PROPERTY_KEY;
        final TextAttributes baseAttrs = colorsScheme.getAttributes(baseAttrKey);
        if (getProperty().getPsiElement().isValid()) {
          if (myInspectedPropertyNodeInfo != null) {
            TextAttributes highlightingAttributes = myInspectedPropertyNodeInfo.getTextAttributes(colorsScheme);
            if (highlightingAttributes != null) {
              return TextAttributes.merge(baseAttrs, highlightingAttributes);
            }
          }
        }
        return baseAttrs;
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
