package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;

import java.awt.LayoutManager;

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
    return componentClassName.replace("$", ".");
  }

  protected void generateLayoutWithGaps(final LwContainer component,
                                        final FormSourceCodeGenerator generator,
                                        final String variable,
                                        final Class<? extends LayoutManager> layoutClass) {
    generator.startMethodCall(variable, "setLayout");

    generator.startConstructor(layoutClass.getName());
    generator.push(Utils.getHGap(component.getLayout()));
    generator.push(Utils.getVGap(component.getLayout()));
    generator.endConstructor();

    generator.endMethod();
  }
}
