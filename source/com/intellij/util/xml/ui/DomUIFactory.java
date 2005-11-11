/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericValue;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.impl.ui.*;
import com.intellij.util.xml.reflect.DomMethodsInfo;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public class DomUIFactory {

  public static <T> DomUIControl createControl(GenericValue<T> element) {
    try {
      Type type = DomUtil.extractParameterClassFromGenericType(element.getDomElementType());
      final Method getValueMethod = GenericValue.class.getMethod("getValue");
      final Method setValueMethod = findMethod(GenericValue.class, "setValue");
      final Method getStringMethod = GenericValue.class.getMethod("getStringValue");
      final Method setStringMethod = findMethod(GenericValue.class, "setStringValue");
      if (type.equals(boolean.class) || type.equals(Boolean.class)) {
        return new BooleanControl(element, getValueMethod, setValueMethod);
      }
      else if (type.equals(String.class)) {
        return new StringControl(element, getValueMethod, setValueMethod);
      }
      else if (type.equals(PsiClass.class)) {
        return new PsiClassControl(element, getStringMethod, setStringMethod);
      }
      else if (type instanceof Class && Enum.class.isAssignableFrom((Class<?>)type)) {
        return new EnumControl(element, (Class)type, getStringMethod, setStringMethod);
      }
      throw new IllegalArgumentException("Not supported: " + type);
    }
    catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }


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

  public static <T extends DomElement> CollectionControl<T> createCollectionControl(DomElement element, String tagName) {
    final DomMethodsInfo methodsInfo = element.getMethodsInfo();
    final Method addMethod = methodsInfo.getCollectionAddMethod(tagName);
    final Method getMethod = methodsInfo.getCollectionGetMethod(tagName);
    return new CollectionControl<T>(element, getMethod, addMethod);
  }

}
