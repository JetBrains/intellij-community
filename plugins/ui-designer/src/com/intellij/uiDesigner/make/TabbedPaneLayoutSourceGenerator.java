package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwTabbedPane;
import com.intellij.psi.PsiKeyword;

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
    if (tabConstraints.myIcon != null || tabConstraints.myToolTip != null) {
      if (tabConstraints.myIcon == null) {
        generator.pushVar(PsiKeyword.NULL);
      }
      else {
        generator.pushIcon(tabConstraints.myIcon);
      }
    }
    generator.pushVar(variable);
    if (tabConstraints.myToolTip != null) {
      generator.push(tabConstraints.myToolTip);
    }
    generator.endMethod();

    int index = component.getParent().indexOfComponent(component);
    if (tabConstraints.myDisabledIcon != null) {
      generator.startMethodCall(parentVariable, "setDisabledIconAt");
      generator.push(index);
      generator.pushIcon(tabConstraints.myDisabledIcon);
      generator.endMethod();
    }
    if (!tabConstraints.myEnabled) {
      generator.startMethodCall(parentVariable, "setEnabledAt");
      generator.push(index);
      generator.push(tabConstraints.myEnabled);
      generator.endMethod();
    }
  }
}
