package com.intellij.uiDesigner.make;

import com.intellij.uiDesigner.compiler.GridBagConverter;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class GridBagLayoutSourceGenerator extends LayoutSourceGenerator {
  private boolean myHaveGbc = false;
  @NonNls private static TIntObjectHashMap<String> myFillMap = new TIntObjectHashMap<String>();
  @NonNls private static TIntObjectHashMap<String> myAnchorMap = new TIntObjectHashMap<String>();

  static {
    myFillMap.put(GridBagConstraints.HORIZONTAL, "GridBagConstraints.HORIZONTAL");
    myFillMap.put(GridBagConstraints.VERTICAL, "GridBagConstraints.VERTICAL");
    myFillMap.put(GridBagConstraints.BOTH, "GridBagConstraints.BOTH");

    myAnchorMap.put(GridBagConstraints.NORTHWEST, "GridBagConstraints.NORTHWEST");
    myAnchorMap.put(GridBagConstraints.NORTH, "GridBagConstraints.NORTH");
    myAnchorMap.put(GridBagConstraints.NORTHEAST, "GridBagConstraints.NORTHEAST");
    myAnchorMap.put(GridBagConstraints.EAST, "GridBagConstraints.EAST");
    myAnchorMap.put(GridBagConstraints.SOUTHEAST, "GridBagConstraints.SOUTHEAST");
    myAnchorMap.put(GridBagConstraints.SOUTH, "GridBagConstraints.SOUTH");
    myAnchorMap.put(GridBagConstraints.SOUTHWEST, "GridBagConstraints.SOUTHWEST");
    myAnchorMap.put(GridBagConstraints.WEST, "GridBagConstraints.WEST");
  }

  @Override
  public void generateContainerLayout(final LwContainer container,
                                      final FormSourceCodeGenerator generator,
                                      final String variable) {
    generator.startMethodCall(variable, "setLayout");

    generator.startConstructor(GridBagLayout.class.getName());
    generator.endConstructor();

    generator.endMethod();
  }

  @Override
  public void generateComponentLayout(final LwComponent component,
                                      final FormSourceCodeGenerator generator,
                                      final String variable,
                                      final String parentVariable) {
    GridBagConstraints gbc;
    if (component.getCustomLayoutConstraints() instanceof GridBagConstraints) {
      gbc = (GridBagConstraints) component.getCustomLayoutConstraints();
    }
    else {
      gbc = new GridBagConstraints();
    }

    GridBagConverter.constraintsToGridBag(component.getConstraints(), gbc);

    generateGridBagConstraints(generator, gbc, variable, parentVariable);
  }

  private void generateConversionResult(final FormSourceCodeGenerator generator,
                                        final GridBagConverter.Result result,
                                        final String variable, final String parentVariable) {
    checkSetSize(generator, variable, "setMinimumSize", result.minimumSize);
    checkSetSize(generator, variable, "setPreferredSize", result.preferredSize);
    checkSetSize(generator, variable, "setMaximumSize", result.maximumSize);

    generateGridBagConstraints(generator, result.constraints, variable, parentVariable);
  }

  private void generateGridBagConstraints(final FormSourceCodeGenerator generator,
                                          final GridBagConstraints constraints,
                                          final String variable,
                                          final String parentVariable) {
    if (!myHaveGbc) {
      generator.append("GridBagConstraints gbc;\n");
      myHaveGbc = true;
    }
    generator.append("gbc = new GridBagConstraints();\n");

    GridBagConstraints defaults = new GridBagConstraints();
    if (defaults.gridx != constraints.gridx) {
      setIntField(generator, "gridx", constraints.gridx);
    }
    if (defaults.gridy != constraints.gridy) {
      setIntField(generator, "gridy", constraints.gridy);
    }
    if (defaults.gridwidth != constraints.gridwidth) {
      setIntField(generator, "gridwidth", constraints.gridwidth);
    }
    if (defaults.gridheight != constraints.gridheight) {
      setIntField(generator, "gridheight", constraints.gridheight);
    }
    if (defaults.weightx != constraints.weightx) {
      setDoubleField(generator, "weightx", constraints.weightx);
    }
    if (defaults.weighty != constraints.weighty) {
      setDoubleField(generator, "weighty", constraints.weighty);
    }
    if (defaults.anchor != constraints.anchor) {
      setIntField(generator, "anchor", constraints.anchor, myAnchorMap);
    }
    if (defaults.fill != constraints.fill) {
      setIntField(generator, "fill", constraints.fill, myFillMap);
    }
    if (defaults.ipadx != constraints.ipadx) {
      setIntField(generator, "ipadx", constraints.ipadx);
    }
    if (defaults.ipady != constraints.ipady) {
      setIntField(generator, "ipady", constraints.ipady);
    }
    if (!defaults.insets.equals(constraints.insets)) {
      generator.append("gbc.insets=");
      generator.newInsets(constraints.insets);
    }

    generator.startMethodCall(parentVariable, "add");
    generator.pushVar(variable);
    generator.pushVar("gbc");
    generator.endMethod();
  }

  private static void setIntField(final FormSourceCodeGenerator generator, @NonNls final String fieldName, final int value) {
    generator.append("gbc.");
    generator.append(fieldName);
    generator.append("=");
    generator.append(value);
    generator.append(";\n");
  }

  private static void setIntField(final FormSourceCodeGenerator generator, @NonNls final String fieldName, final int value,
                                  final TIntObjectHashMap<String> map) {
    generator.append("gbc.");
    generator.append(fieldName);
    generator.append("=");
    if (map.containsKey(value)) {
      generator.append(map.get(value));
    }
    else {
      generator.append(value);
    }
    generator.append(";\n");
  }

  private static void setDoubleField(final FormSourceCodeGenerator generator, @NonNls final String fieldName, final double value) {
    generator.append("gbc.");
    generator.append(fieldName);
    generator.append("=");
    generator.append(value);
    generator.append(";\n");
  }

  private static void checkSetSize(final FormSourceCodeGenerator generator,
                                   final String variable,
                                   @NonNls final String methodName,
                                   final Dimension dimension) {
    if (dimension != null) {
      generator.startMethodCall(variable, methodName);
      generator.newDimension(dimension);
      generator.endMethod();
    }
  }

  @Override
  public String mapComponentClass(final String componentClassName) {
    if (componentClassName.equals(Spacer.class.getName())) {
      return JPanel.class.getName();
    }
    return super.mapComponentClass(componentClassName);
  }
}
