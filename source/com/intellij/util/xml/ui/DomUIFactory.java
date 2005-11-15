/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericValue;
import com.intellij.util.xml.impl.ui.*;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.openapi.diagnostic.Logger;

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

  public static <T extends DomElement> CollectionControl<T> createCollectionControl(DomElement element, DomCollectionChildDescription description) {
    final Class aClass = DomUtil.extractParameterClassFromGenericType(description.getType());
    if (aClass != null) {
      return new GenericValueCollectionControl(element, description, createGenericValueControl(aClass, null));
    }
    return new CollectionControl<T>(element, description);
  }

}
