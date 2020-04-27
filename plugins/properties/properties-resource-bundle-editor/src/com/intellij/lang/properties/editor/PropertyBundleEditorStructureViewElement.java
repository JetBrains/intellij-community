// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.editor.inspections.InspectedPropertyProblems;
import com.intellij.lang.properties.editor.inspections.ResourceBundleEditorProblemDescriptor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;

public class PropertyBundleEditorStructureViewElement extends PropertyStructureViewElement {
  private volatile InspectedPropertyProblems myInspectedPropertyProblems;

  public PropertyBundleEditorStructureViewElement(@NotNull IProperty property,
                                                  @NotNull BooleanSupplier grouped) {
    super(property, grouped);
  }

  @Override
  protected TextAttributes getErrorTextAttributes(EditorColorsScheme colorsScheme) {
    if (myInspectedPropertyProblems != null) {
      return myInspectedPropertyProblems.getTextAttributes(colorsScheme);
    }
    return null;
  }

  public Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey> @NotNull [] getProblemDescriptors() {
    return myInspectedPropertyProblems == null ? new Pair[0] : myInspectedPropertyProblems.getDescriptors();
  }

  public void setInspectedPropertyProblems(InspectedPropertyProblems inspectedPropertyProblems) {
    myInspectedPropertyProblems = inspectedPropertyProblems;
  }
}
