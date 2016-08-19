/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lang.properties.editor.inspections;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.SortedSet;

public class InspectedPropertyNodeInfo {
  private final Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>[] myDescriptors;
  private final SortedSet<HighlightInfoType> myHighlightTypes;

  public InspectedPropertyNodeInfo(Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>[] descriptors, SortedSet<HighlightInfoType> types) {
    myDescriptors = descriptors;
    myHighlightTypes = types;
  }

  public Pair<ResourceBundleEditorProblemDescriptor, HighlightDisplayKey>[] getDescriptors() {
    return myDescriptors;
  }

  @Nullable
  public TextAttributes getTextAttributes(EditorColorsScheme scheme) {
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
