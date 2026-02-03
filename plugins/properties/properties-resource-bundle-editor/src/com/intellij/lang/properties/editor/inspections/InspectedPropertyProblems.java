// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor.inspections;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.SortedSet;

public class InspectedPropertyProblems {
  private final Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>[] myDescriptors;
  private final SortedSet<? extends HighlightInfoType> myHighlightTypes;

  public InspectedPropertyProblems(Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>[] descriptors, SortedSet<? extends HighlightInfoType> types) {
    myDescriptors = descriptors;
    myHighlightTypes = types;
  }

  public Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>[] getDescriptors() {
    return myDescriptors;
  }

  public @Nullable TextAttributes getTextAttributes(EditorColorsScheme scheme) {
    TextAttributes mixedAttributes = null;
    for (HighlightInfoType type : myHighlightTypes) {
      final TextAttributes current = scheme.getAttributes(type.getAttributesKey());
      if (mixedAttributes == null) {
        mixedAttributes = current;
      } else {
        mixedAttributes = TextAttributes.merge(mixedAttributes, current);
      }
    }
    return mixedAttributes;
  }
}
