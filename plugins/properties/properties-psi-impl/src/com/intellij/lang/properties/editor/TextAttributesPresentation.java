// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;

public interface TextAttributesPresentation extends ColoredItemPresentation {
  TextAttributes getTextAttributes(EditorColorsScheme colorsScheme);
}
