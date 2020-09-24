// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.openapi.util.NlsSafe;
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

  @Override
  protected void customize(@NotNull IconDescriptor value) {
    setIcon(value.getIcon());
    @NlsSafe String path = value.getIconPath();
    setText(path);
  }
}
