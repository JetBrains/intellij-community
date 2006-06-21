/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.lw.IconDescriptor;
import com.intellij.uiDesigner.propertyInspector.properties.IntroIconProperty;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
*/
public class IconRenderer extends LabelPropertyRenderer<IconDescriptor> {
  @Override
  public JLabel getComponent(final RadRootContainer rootContainer, final IconDescriptor iconDescriptor,
                             final boolean selected, final boolean hasFocus) {
    if (iconDescriptor != null) {
      IntroIconProperty.ensureIconLoaded(rootContainer.getModule(), iconDescriptor);
    }
    final JLabel component = super.getComponent(rootContainer, iconDescriptor, selected, hasFocus);
    if (!selected && iconDescriptor != null && iconDescriptor.getIcon() == null) {
      setForeground(Color.RED);
    }
    return component;
  }

  protected void customize(@NotNull IconDescriptor value) {
    setIcon(value.getIcon());
    setText(value.getIconPath());
  }
}
