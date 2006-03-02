/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;

import java.awt.FlowLayout;

/**
 * @author yole
 */
public class FlowLayoutSourceGenerator extends LayoutSourceGenerator {
  @NonNls private static final TIntObjectHashMap<String> myAlignMap = new TIntObjectHashMap<String>();

  static {
    myAlignMap.put(FlowLayout.CENTER, "FlowLayout.CENTER");
    myAlignMap.put(FlowLayout.LEFT, "FlowLayout.LEFT");
    myAlignMap.put(FlowLayout.RIGHT, "FlowLayout.RIGHT");
    myAlignMap.put(FlowLayout.LEADING, "FlowLayout.LEADING");
    myAlignMap.put(FlowLayout.TRAILING, "FlowLayout.TRAILING");
  }

  @Override public void generateContainerLayout(final LwContainer component,
                                                final FormSourceCodeGenerator generator,
                                                final String variable) {
    generator.startMethodCall(variable, "setLayout");

    FlowLayout layout = (FlowLayout) component.getLayout();

    generator.startConstructor(FlowLayout.class.getName());
    generator.push(layout.getAlignment(), myAlignMap);
    generator.push(layout.getHgap());
    generator.push(layout.getVgap());
    generator.endConstructor();

    generator.endMethod();
  }

  public void generateComponentLayout(final LwComponent component,
                                      final FormSourceCodeGenerator generator,
                                      final String variable,
                                      final String parentVariable) {
    generator.startMethodCall(parentVariable, "add");
    generator.pushVar(variable);
    generator.endMethod();
  }
}
