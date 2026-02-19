// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwSplitPane;
import org.jetbrains.annotations.NonNls;


public class SplitPaneLayoutSourceGenerator extends LayoutSourceGenerator {
  @Override
  public void generateComponentLayout(final LwComponent component,
                                      final FormSourceCodeGenerator generator,
                                      final String variable,
                                      final String parentVariable) {
    final @NonNls String methodName =
      LwSplitPane.POSITION_LEFT.equals(component.getCustomLayoutConstraints()) ?
      "setLeftComponent" :
      "setRightComponent";

    generator.startMethodCall(parentVariable, methodName);
    generator.pushVar(variable);
    generator.endMethod();
  }
}
