/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public final class IntFieldProperty extends Property<RadComponent, Integer> {
  private final LabelPropertyRenderer<Integer> myRenderer;
  private final IntEditor myEditor;
  @NotNull private final Property myParent;
  private String myFieldName;
  private final Object myTemplateValue;
  @NonNls private static final String METHOD_CLONE = "clone";

  public IntFieldProperty(@NotNull final Property parent, @NonNls final String fieldName, final int lowBoundary, final Object templateValue) {
    super(parent, fieldName);
    myParent = parent;
    myFieldName = fieldName;
    myTemplateValue = templateValue;
    myRenderer = new LabelPropertyRenderer<Integer>();
    myEditor = new IntEditor(lowBoundary);
  }

  public Integer getValue(final RadComponent component) {
    //noinspection unchecked
    final Object parentValue = myParent.getValue(component);
    if (parentValue == null) return 0;
    try {
      return parentValue.getClass().getField(myFieldName).getInt(parentValue);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected void setValueImpl(final RadComponent component,final Integer value) throws Exception{
    //noinspection unchecked
    Object parentValue = myParent.getValue(component);
    if (parentValue == null) {
      parentValue = myTemplateValue;
    }
    else {
      final Method method = parentValue.getClass().getMethod(METHOD_CLONE, ArrayUtil.EMPTY_CLASS_ARRAY);
      parentValue = method.invoke(parentValue);
    }
    parentValue.getClass().getField(myFieldName).setInt(parentValue, value.intValue());
    //noinspection unchecked
    myParent.setValue(component, parentValue);
  }

  @NotNull
  public PropertyRenderer<Integer> getRenderer() {
    return myRenderer;
  }

  public PropertyEditor<Integer> getEditor() {
    return myEditor;
  }
}
