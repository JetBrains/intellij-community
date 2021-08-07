// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.make;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import org.jetbrains.annotations.NonNls;

import java.awt.*;


public class BorderLayoutSourceGenerator extends LayoutSourceGenerator {

  @Override public void generateContainerLayout(final LwContainer component, final FormSourceCodeGenerator generator, final String variable) {
    generateLayoutWithGaps(component, generator, variable, BorderLayout.class);
  }

  @Override
  public void generateComponentLayout(final LwComponent component,
                                      @NonNls final FormSourceCodeGenerator generator,
                                      final String variable,
                                      final String parentVariable) {
    generator.startMethodCall(parentVariable, "add");
    generator.pushVar(variable);
    generator.checkParameter();
    generator.append("BorderLayout." + StringUtil.toUpperCase(((String) component.getCustomLayoutConstraints())));
    generator.endMethod();
  }
}
