/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.lang.properties.editor;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.lang.properties.editor.inspections.InspectedPropertyProblems;
import com.intellij.lang.properties.editor.inspections.ResourceBundleEditorProblemDescriptor;
import com.intellij.lang.properties.editor.inspections.ResourceBundleEditorRenderer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.BooleanSupplier;

public class PropertyStructureViewElement implements StructureViewTreeElement, ResourceBundleEditorViewElement {
  private static final TextAttributesKey GROUP_KEY;

  public static final String PROPERTY_GROUP_KEY_TEXT = "<property>";
  @NotNull
  private final IProperty myProperty;
  @NotNull
  private final BooleanSupplier myGrouped;
  private String myPresentableName;

  static {
    TextAttributes groupKeyTextAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PropertiesHighlighter.PROPERTY_KEY).clone();
    groupKeyTextAttributes.setFontType(Font.ITALIC);
    GROUP_KEY = TextAttributesKey.createTextAttributesKey("GROUP_KEY", groupKeyTextAttributes);
  }

  private volatile InspectedPropertyProblems myInspectedPropertyProblems;

  public PropertyStructureViewElement(@NotNull IProperty property, @NotNull BooleanSupplier grouped) {
    myProperty = property;
    myGrouped = grouped;
  }

  @Nullable
  public IProperty getProperty() {
    return getPsiElement() != null ? myProperty : null;
  }

  @Nullable
  public PsiElement getPsiElement() {
    PsiElement element = myProperty.getPsiElement();
    return element.isValid() ? element : null;
  }

  @NotNull
  @Override
  public IProperty[] getProperties() {
    return new IProperty[] {myProperty};
  }

  @Nullable
  @Override
  public PsiFile[] getFiles() {
    return null;
  }

  public void setPresentableName(final String presentableName) {
    myPresentableName = presentableName;
  }

  private String getPresentableName() {
    return myGrouped.getAsBoolean() ? myPresentableName : null;
  }

  @Override
  public IProperty getValue() {
    return getProperty();
  }

  @Override
  @NotNull
  public StructureViewTreeElement[] getChildren() {
    return EMPTY_ARRAY;
  }

  @NotNull
  public Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>[] getProblemDescriptors() {
    return myInspectedPropertyProblems == null ? new Pair[0] : myInspectedPropertyProblems.getDescriptors();
  }

  public void setInspectedPropertyProblems(InspectedPropertyProblems inspectedPropertyProblems) {
    myInspectedPropertyProblems = inspectedPropertyProblems;
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return new ResourceBundleEditorRenderer.TextAttributesPresentation() {

      @Nullable
      @Override
      public TextAttributesKey getTextAttributesKey() {
        return (getPresentableName() != null && getPresentableName().isEmpty()) ? GROUP_KEY : null;
      }

      @Override
      public String getPresentableText() {
        IProperty property = getProperty();
        if (property == null) return null;
        return getPresentableName() == null ? property.getName() : getPresentableName().isEmpty() ? PROPERTY_GROUP_KEY_TEXT : getPresentableName();
      }

      @Override
      public String getLocationString() {
        return null;
      }

      @Override
      public Icon getIcon(boolean open) {
        return myProperty.getIcon(0);
      }

      @Override
      public TextAttributes getTextAttributes(EditorColorsScheme colorsScheme) {
        final TextAttributesKey baseAttrKey =
          (getPresentableName() != null && getPresentableName().isEmpty()) ? GROUP_KEY : PropertiesHighlighter.PROPERTY_KEY;
        final TextAttributes baseAttrs = colorsScheme.getAttributes(baseAttrKey);
        if (getPsiElement() != null) {
          if (myInspectedPropertyProblems != null) {
            TextAttributes highlightingAttributes = myInspectedPropertyProblems.getTextAttributes(colorsScheme);
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
    myProperty.navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return myProperty.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return myProperty.canNavigateToSource();
  }
}
