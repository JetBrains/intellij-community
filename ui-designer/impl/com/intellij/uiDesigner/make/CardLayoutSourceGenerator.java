/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;

import java.awt.CardLayout;

/**
 * @author yole
 */
public class CardLayoutSourceGenerator extends LayoutSourceGenerator {
  @Override
  public void generateContainerLayout(final LwContainer component, final FormSourceCodeGenerator generator, final String variable) {
    generateLayoutWithGaps(component, generator, variable, CardLayout.class);
  }

  public void generateComponentLayout(final LwComponent component,
                                      final FormSourceCodeGenerator generator,
                                      final String variable,
                                      final String parentVariable) {
    generator.startMethodCall(parentVariable, "add");
    generator.pushVar(variable);
    generator.push((String) component.getCustomLayoutConstraints());
    generator.endMethod();
  }
}
