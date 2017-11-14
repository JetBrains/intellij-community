/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.FormEditingUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class IntRegexEditor<T> extends AbstractTextFieldEditor<T> {
  @NonNls private final Pattern myPattern;
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
