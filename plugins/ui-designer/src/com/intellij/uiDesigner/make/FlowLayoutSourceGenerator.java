// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NonNls;

import java.awt.*;


public final class FlowLayoutSourceGenerator extends LayoutSourceGenerator {
  private static final @NonNls Int2ObjectMap<String> myAlignMap = new Int2ObjectOpenHashMap<>();

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

  @Override
  public void generateComponentLayout(final LwComponent component,
                                      final FormSourceCodeGenerator generator,
                                      final String variable,
                                      final String parentVariable) {
    generator.startMethodCall(parentVariable, "add");
    generator.pushVar(variable);
    generator.endMethod();
  }
}
