/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.lw.IconDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
*/
public class IconRenderer extends LabelPropertyRenderer<IconDescriptor> {
  protected void customize(@NotNull IconDescriptor value) {
    setIcon(value.getIcon());
    setText(value.getIconPath());
  }
}
