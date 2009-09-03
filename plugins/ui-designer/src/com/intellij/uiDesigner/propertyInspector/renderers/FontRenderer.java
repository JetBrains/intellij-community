/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.lw.FontDescriptor;
import com.intellij.uiDesigner.propertyInspector.properties.IntroFontProperty;

/**
 * @author yole
 */
public class FontRenderer extends LabelPropertyRenderer<FontDescriptor> {
  protected void customize(FontDescriptor value) {
    setText(IntroFontProperty.descriptorToString(value));
  }
}
