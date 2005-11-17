/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericValue;
import com.intellij.util.xml.impl.ui.*;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.ui.ColumnInfo;

import javax.swing.table.TableCellEditor;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public class DomUIFactory {
  private static final Logger LOG;
  private static Method GET_VALUE_METHOD = null;
  private static Method SET_VALUE_METHOD = null;
  private static Method GET_STRING_METHOD = null;
  private static Method SET_STRING_METHOD = null;

  static {
    LOG = Logger.getInstance("#com.intellij.util.xml.ui.DomUIFactory");
    try {
      GET_VALUE_METHOD = GenericValue.class.getMethod("getValue");
      GET_STRING_METHOD = GenericValue.class.getMethod("getStringValue");
      SET_VALUE_METHOD = findMethod(GenericValue.class, "setValue");
      SET_STRING_METHOD = findMethod(GenericValue.class, "setStringValue");
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
  }


  public static DomUIControl createControl(GenericValue element) {
    return createGenericValueControl(DomUtil.extractParameterClassFromGenericType(element.getDomElementType()), element);
  }

  private static BaseControl createGenericValueControl(final Type type, final GenericValue element) {
    if (type.equals(boolean.class) || type.equals(Boolean.class)) {
      return new BooleanControl(element, GET_VALUE_METHOD, SET_VALUE_METHOD);
    }
    else if (type.equals(String.class)) {
      return new StringControl(element, GET_VALUE_METHOD, SET_VALUE_METHOD);
    }
    else if (type.equals(PsiClass.class)) {
      return new PsiClassControl(element, GET_STRING_METHOD, SET_STRING_METHOD);
    }
    else if (type instanceof Class && Enum.class.isAssignableFrom((Class)type)) {
      return new EnumControl(element, (Class)type, GET_STRING_METHOD, SET_STRING_METHOD);
    }
    throw new IllegalArgumentException("Not supported: " + type);
  }

  private static Method findMethod(Class clazz, String methodName) {
    final Method[] methods = clazz.getMethods();
    for (Method method : methods) {
      if (methodName.equals(method.getName())) {
        return method;
      }
    }
    return null;
  }

  private static TableCellEditor createCellEditor(DomElement element, Class type) {
    if (String.class.equals(type)) {
      return new DefaultCellEditor(removeBorder(new JTextField()));
    }

    if (PsiClass.class.equals(type)) {
      return new PsiClassTableCellEditor(element);
    }

    if (Enum.class.equals(type)) {
      return new DefaultCellEditor(removeBorder(EnumControl.createEnumComboBox(type)));
    }

    assert false : "Type not supported: " + type;
    return null;
  }

  private static <T extends JComponent> T removeBorder(final T component) {
    component.setBorder(new EmptyBorder(0, 0, 0, 0));
    return component;
  }

  public static DomUIControl createCollectionControl(DomElement element, DomCollectionChildDescription description) {
    final Class aClass = DomUtil.extractParameterClassFromGenericType(description.getType());
    if (aClass != null) {
      return new DomCollectionControl<GenericValue<?>>(element, description, createColumnInfo(description, element, aClass));
    }

    return new CollectionControl(element, description);
  }

  private static ColumnInfo createColumnInfo(final DomCollectionChildDescription description,
                                                         final DomElement element,
                                                         final Class aClass) {
    final String presentableName = description.getCommonPresentableName(element);
    if (Boolean.class.equals(aClass) || boolean.class.equals(aClass)) {
      return new BooleanColumnInfo(presentableName);
    }

    return new GenericValueColumnInfo(presentableName, aClass, createCellEditor(element, aClass));
  }

}
