// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.ui.JBColor;
import com.intellij.uiDesigner.lw.FontDescriptor;
import com.intellij.uiDesigner.propertyInspector.properties.IntroFontProperty;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class FontRenderer extends LabelPropertyRenderer<FontDescriptor> {
  @Override
  protected void customize(@NotNull FontDescriptor value) {
    setText(IntroFontProperty.descriptorToString(value));
    setForeground(value.isValid() ? null : JBColor.red);
  }
}
