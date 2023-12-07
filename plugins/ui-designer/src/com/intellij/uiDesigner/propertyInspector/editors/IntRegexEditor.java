// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class IntRegexEditor<T> extends AbstractTextFieldEditor<T> {
  private final @NonNls Pattern myPattern;
  private final Class<T> myValueClass;
  private final LabelPropertyRenderer<T> myRenderer;
  private final int[] myMinValues;

  public IntRegexEditor(Class<T> valueClass, LabelPropertyRenderer<T> renderer, final int[] minValues) {
    myMinValues = minValues;
    myValueClass = valueClass;
    myRenderer = renderer;

    @NonNls StringBuilder regexBuilder = new StringBuilder("\\[?(-?\\d+)");
    for(int i=1; i<myMinValues.length; i++) {
      regexBuilder.append(",\\s*(-?\\d+)");
    }
    regexBuilder.append("\\]?");
    myPattern = Pattern.compile(regexBuilder.toString());
  }

  @Override
  protected void setValueFromComponent(final RadComponent component, final T value) {
    RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
    JLabel label = myRenderer.getComponent(root, value, false, false);
    myTf.setText(label.getText());
  }

  @Override
  public T getValue() throws Exception {
    final Matcher matcher = myPattern.matcher(myTf.getText());
    if (!matcher.matches()) {
      throw new Exception("Incorrect dimension format");
    }

    Class[] paramTypes = new Class[myMinValues.length];
    Integer[] params = new Integer[myMinValues.length];
    for(int i=0; i<myMinValues.length; i++) {
      paramTypes [i] = int.class;
      final int value = Integer.parseInt(matcher.group(i + 1));
      if (value < myMinValues [i]) {
        throw new RuntimeException(UIDesignerBundle.message("error.value.should.not.be.less", myMinValues [i]));
      }
      params [i] = value;
    }

    return myValueClass.getConstructor(paramTypes).newInstance((Object[])params);
  }
}
