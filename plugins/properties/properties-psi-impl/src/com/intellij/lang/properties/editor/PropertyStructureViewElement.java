// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.properties.editor;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Font;
import java.util.function.BooleanSupplier;

public class PropertyStructureViewElement implements StructureViewTreeElement, ResourceBundleEditorViewElement {
  private static final TextAttributesKey GROUP_KEY;

  private final @NotNull IProperty myProperty;
  private final @NotNull BooleanSupplier myGrouped;
  private String myPresentableName;

  static {
    TextAttributes groupKeyTextAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(PropertiesHighlighter.PropertiesComponent.PROPERTY_KEY.getTextAttributesKey()).clone();
    groupKeyTextAttributes.setFontType(Font.ITALIC);
    GROUP_KEY = TextAttributesKey.createTextAttributesKey("GROUP_KEY", groupKeyTextAttributes);
  }

  public PropertyStructureViewElement(@NotNull IProperty property, @NotNull BooleanSupplier grouped) {
    myProperty = property;
    myGrouped = grouped;
  }

  @RequiresReadLock
  public @Nullable IProperty getProperty() {
    return getPsiElement() != null ? myProperty : null;
  }

  @RequiresReadLock
  public @Nullable PsiElement getPsiElement() {
    PsiElement element = myProperty.getPsiElement();
    return element.isValid() ? element : null;
  }

  @Override
  public IProperty @NotNull [] getProperties() {
    return new IProperty[]{myProperty};
  }

  @Override
  public PsiFile @Nullable [] getFiles() {
    return null;
  }

  public void setPresentableName(final String presentableName) {
    myPresentableName = presentableName;
  }

  private String getPresentableName() {
    return myGrouped.getAsBoolean() ? myPresentableName : null;
  }

  @RequiresReadLock
  @Override
  public IProperty getValue() {
    return getProperty();
  }

  @Override
  public StructureViewTreeElement @NotNull [] getChildren() {
    return EMPTY_ARRAY;
  }

  protected TextAttributes getErrorTextAttributes(EditorColorsScheme colorsScheme) {
    return null;
  }

  @Override
  public @NotNull ItemPresentation getPresentation() {
    return new TextAttributesPresentation() {

      @Override
      public @Nullable TextAttributesKey getTextAttributesKey() {
        return (getPresentableName() != null && getPresentableName().isEmpty()) ? GROUP_KEY : null;
      }

      @Override
      public String getPresentableText() {
        String presentableName = getPresentableName();
        if (presentableName != null) {
          return presentableName.isEmpty()
                 ? PropertiesBundle.message("structure.view.empty.property.presentation")
                 : presentableName;
        }
        return ReadAction.computeBlocking(() -> {
          IProperty property = getProperty();
          return property == null ? null : property.getName();
        });
      }

      @Override
      public Icon getIcon(boolean open) {
        return myProperty.getIcon(0);
      }

      @Override
      public TextAttributes getTextAttributes(EditorColorsScheme colorsScheme) {
        final TextAttributesKey baseAttrKey = (getPresentableName() != null && getPresentableName().isEmpty())
                                              ? GROUP_KEY
                                              : PropertiesHighlighter.PropertiesComponent.PROPERTY_KEY.getTextAttributesKey();
        final TextAttributes baseAttrs = colorsScheme.getAttributes(baseAttrKey);
        TextAttributes highlightingAttributes = ReadAction.computeBlocking(() -> getPsiElement() == null ? null : getErrorTextAttributes(colorsScheme));
        return TextAttributes.merge(baseAttrs, highlightingAttributes);
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
