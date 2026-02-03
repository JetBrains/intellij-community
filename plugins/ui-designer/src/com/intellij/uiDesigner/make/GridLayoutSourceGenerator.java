// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;


public final class GridLayoutSourceGenerator extends LayoutSourceGenerator {
  private static final Int2ObjectMap<String> myAnchors = fillMap(GridConstraints.class, "ANCHOR_");
  private static final Int2ObjectMap<String> myFills = fillMap(GridConstraints.class, "FILL_");

  public static final GridLayoutSourceGenerator INSTANCE = new GridLayoutSourceGenerator();

  @Override
  public void generateContainerLayout(final LwContainer container, final FormSourceCodeGenerator generator,
                                      final String variable) {
    if (container.isXY()) {
      if (container.getComponentCount() != 0) {
        throw new IllegalStateException("only empty xys are accepted");
      }
      // no layout needed
    }
    else {
      if (container.isGrid()) {
        final GridLayoutManager layout = (GridLayoutManager)container.getLayout();

        generator.startMethodCall(variable, "setLayout");

        generator.startConstructor(GridLayoutManager.class.getName());

        generator.push(layout.getRowCount());
        generator.push(layout.getColumnCount());

        generator.newInsets(layout.getMargin());

        generator.push(layout.getHGap());
        generator.push(layout.getVGap());

        if (layout.isSameSizeHorizontally() || layout.isSameSizeVertically()) {
          // values differ from the defaults - use appropriate constructor
          generator.push(layout.isSameSizeHorizontally());
          generator.push(layout.isSameSizeVertically());
        }

        generator.endConstructor(); // GridLayoutManager

        generator.endMethod();
      }
      else {
        throw new IllegalArgumentException("unknown layout: " + container.getLayout());
      }
    }
  }

  @Override
  public void generateComponentLayout(final LwComponent component, final FormSourceCodeGenerator generator,
                                      final String variable, final String parentVariable) {
    generator.startMethodCall(parentVariable, "add");
    generator.pushVar(variable);
    addNewGridConstraints(generator, component);
    generator.endMethod();
  }

  private static void addNewGridConstraints(final FormSourceCodeGenerator generator, final LwComponent component) {
    final GridConstraints constraints = component.getConstraints();

    generator.startConstructor(GridConstraints.class.getName());
    generator.push(constraints.getRow());
    generator.push(constraints.getColumn());
    generator.push(constraints.getRowSpan());
    generator.push(constraints.getColSpan());
    generator.push(constraints.getAnchor(), myAnchors);
    generator.push(constraints.getFill(), myFills);
    pushSizePolicy(generator, constraints.getHSizePolicy());
    pushSizePolicy(generator, constraints.getVSizePolicy());
    generator.newDimensionOrNull(constraints.myMinimumSize);
    generator.newDimensionOrNull(constraints.myPreferredSize);
    generator.newDimensionOrNull(constraints.myMaximumSize);
    generator.push(constraints.getIndent());
    generator.push(constraints.isUseParentLayout());
    generator.endConstructor();
  }

  private static void pushSizePolicy(final FormSourceCodeGenerator generator, final int value) {
    final String className = GridConstraints.class.getName();

    @NonNls String presentation;
    if (GridConstraints.SIZEPOLICY_FIXED == value) {
      presentation = className + ".SIZEPOLICY_FIXED";
    }
    else {
      if ((value & GridConstraints.SIZEPOLICY_CAN_SHRINK) != 0) {
        presentation = className + ".SIZEPOLICY_CAN_SHRINK";
      }
      else {
        presentation = null;
      }

      if ((value & GridConstraints.SIZEPOLICY_WANT_GROW) != 0) {
        if (presentation == null) {
          presentation = className + ".SIZEPOLICY_WANT_GROW";
        }
        else {
          presentation += "|" + className + ".SIZEPOLICY_WANT_GROW";
        }
      }
      else if ((value & GridConstraints.SIZEPOLICY_CAN_GROW) != 0) {
        if (presentation == null) {
          presentation = className + ".SIZEPOLICY_CAN_GROW";
        }
        else {
          presentation += "|" + className + ".SIZEPOLICY_CAN_GROW";
        }
      }
      else {
        // ?
        generator.push(value);
        return;
      }
    }

    generator.pushVar(presentation);
  }

  private static Int2ObjectMap<String> fillMap(final Class<GridConstraints> aClass, final @NonNls String prefix) {
    final Int2ObjectMap<String> map = new Int2ObjectOpenHashMap<>();

    final Field[] fields = aClass.getFields();
    for (final Field field : fields) {
      if ((field.getModifiers() & Modifier.STATIC) != 0 && field.getName().startsWith(prefix)) {
        field.setAccessible(true);
        try {
          final int value = field.getInt(aClass);
          map.put(value, aClass.getName() + '.' + field.getName());
        }
        catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
    }

    return map;
  }
}
