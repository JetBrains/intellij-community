package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.lw.LwComponent;

/**
 * @author yole
 */
public class ScrollPaneLayoutSourceGenerator extends LayoutSourceGenerator {
  public void generateComponentLayout(final LwComponent component,
                                      final FormSourceCodeGenerator generator,
                                      final String variable,
                                      final String parentVariable) {
    generator.startMethodCall(parentVariable, "setViewportView");
    generator.pushVar(variable);
    generator.endMethod();
  }
}
