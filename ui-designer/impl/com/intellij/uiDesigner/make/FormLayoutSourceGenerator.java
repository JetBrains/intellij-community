/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.jgoodies.forms.layout.FormLayout;

/**
 * @author yole
 */
public class FormLayoutSourceGenerator extends LayoutSourceGenerator {
  private boolean myHaveCc = false;

  @Override
  public void generateContainerLayout(final LwContainer component, final FormSourceCodeGenerator generator, final String variable) {
    FormLayout layout = (FormLayout) component.getLayout();
    generator.startMethodCall(variable, "setLayout");

    generator.startConstructor(FormLayout.class.getName());
    generator.push(Utils.getEncodedColumnSpecs(layout));
    generator.push(Utils.getEncodedRowSpecs(layout));
    generator.endConstructor();

    generator.endMethod();
  }

  public void generateComponentLayout(final LwComponent component, final FormSourceCodeGenerator generator, final String variable, final String parentVariable) {
    if (!myHaveCc) {
      generator.append("com.jgoodies.forms.layout.CellConstraints cc = new com.jgoodies.forms.layout.CellConstraints();\n");
      myHaveCc = true;
    }
    generator.startMethodCall(parentVariable, "add");
    generator.pushVar(variable);

    GridConstraints constraints = component.getConstraints();
    if (constraints.getColSpan() == 1 && constraints.getRowSpan() == 1) {
      generator.startMethodCall("cc", "xy");
    }
    else if (constraints.getRowSpan() == 1) {
      generator.startMethodCall("cc", "xyw");
    }
    else {
      generator.startMethodCall("cc", "xywh");
    }
    generator.push(constraints.getColumn()+1);
    generator.push(constraints.getRow()+1);
    if (constraints.getColSpan() > 1 || constraints.getRowSpan() > 1) {
      generator.push(constraints.getColSpan());
    }
    if (constraints.getRowSpan() > 1) {
      generator.push(constraints.getRowSpan());
    }
    generator.endMethod();
    generator.endMethod();
  }
}
