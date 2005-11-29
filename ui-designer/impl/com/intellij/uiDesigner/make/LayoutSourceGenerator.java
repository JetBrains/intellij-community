package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.lw.LwComponent;

/**
 * @author yole
 */
public abstract class LayoutSourceGenerator {
  public void generateContainerLayout(final LwComponent component,
                                      final FormSourceCodeGenerator generator,
                                      final String variable) {
  }

  public abstract void generateComponentLayout(final LwComponent component,
                                               final FormSourceCodeGenerator generator,
                                               final String variable,
                                               final String parentVariable);
}
