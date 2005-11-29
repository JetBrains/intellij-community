package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwTabbedPane;

/**
 * @author yole
 */
public class TabbedPaneLayoutSourceGenerator extends LayoutSourceGenerator {
  public void generateComponentLayout(final LwComponent component,
                                      final FormSourceCodeGenerator generator,
                                      final String variable,
                                      final String parentVariable) {
    final LwTabbedPane.Constraints tabConstraints = (LwTabbedPane.Constraints)component.getCustomLayoutConstraints();
    if (tabConstraints == null){
      throw new IllegalArgumentException("tab constraints cannot be null: " + component.getId());
    }

    generator.startMethodCall(parentVariable, "addTab");
    generator.push(tabConstraints.myTitle);
    generator.pushVar(variable);
    generator.endMethod();
  }
}
