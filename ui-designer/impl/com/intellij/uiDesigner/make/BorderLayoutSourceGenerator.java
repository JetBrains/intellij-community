/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * @author yole
 */
public class BorderLayoutSourceGenerator extends LayoutSourceGenerator {


  @Override public void generateContainerLayout(final LwContainer component, final FormSourceCodeGenerator generator, final String variable) {
    generator.startMethodCall(variable, "setLayout");

    BorderLayout layout = (BorderLayout) component.getLayout();

    generator.startConstructor(BorderLayout.class.getName());
    generator.push(layout.getHgap());
    generator.push(layout.getVgap());
    generator.endConstructor();

    generator.endMethod();
  }

  public void generateComponentLayout(final LwComponent component,
                                      @NonNls final FormSourceCodeGenerator generator,
                                      final String variable,
                                      final String parentVariable) {
    generator.startMethodCall(parentVariable, "add");
    generator.pushVar(variable);
    generator.checkParameter();
    generator.append("BorderLayout." + ((String) component.getCustomLayoutConstraints()).toUpperCase());
    generator.endMethod();
  }
}
