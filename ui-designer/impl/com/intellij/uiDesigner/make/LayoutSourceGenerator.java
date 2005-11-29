package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;

/**
 * @author yole
 */
public abstract class LayoutSourceGenerator {
  public void generateContainerLayout(final LwContainer component,
                                      final FormSourceCodeGenerator generator,
                                      final String variable) {
  }

  public abstract void generateComponentLayout(final LwComponent component,
                                               final FormSourceCodeGenerator generator,
                                               final String variable,
                                               final String parentVariable);

  public String mapComponentClass(final String componentClassName) {
    return componentClassName;
  }
}
