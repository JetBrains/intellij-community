// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.lw.LwComponent;


public class ScrollPaneLayoutSourceGenerator extends LayoutSourceGenerator {
  @Override
  public void generateComponentLayout(final LwComponent component,
                                      final FormSourceCodeGenerator generator,
                                      final String variable,
                                      final String parentVariable) {
    generator.startMethodCall(parentVariable, "setViewportView");
    generator.pushVar(variable);
    generator.endMethod();
  }
}
