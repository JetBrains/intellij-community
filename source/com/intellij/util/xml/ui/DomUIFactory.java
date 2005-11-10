/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomMethodsInfo;
import com.intellij.util.xml.GenericValue;
import com.intellij.util.xml.impl.ui.BooleanControl;
import com.intellij.util.xml.impl.ui.CollectionControl;
import com.intellij.util.xml.impl.ui.PsiClassControl;
import com.intellij.util.xml.impl.ui.StringControl;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public class DomUIFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.ui.DomUIFactory");

  public static <T> DomUIControl createControl(GenericValue<T> element, Class<T> aClass) {
    try {
      final Method getValueMethod = aClass.getMethod("getValue");
      final Method setValueMethod = aClass.getMethod("setValue", aClass);
      final Method getStringMethod = aClass.getMethod("getStringValue");
      final Method setStringMethod = aClass.getMethod("setStringValue", aClass);
      if (aClass.equals(boolean.class) || aClass.equals(Boolean.class)) {
        return new BooleanControl(element, getValueMethod, setValueMethod);
      } else if (aClass.equals(String.class)) {
        return new StringControl(element, getValueMethod, setValueMethod);
      } else if (aClass.equals(PsiClass.class)) {
        return new PsiClassControl(element, getStringMethod, setStringMethod);
      }
      throw new IllegalArgumentException("Not supported: " + aClass);
    }
    catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  public static DomUIControl createControl(DomElement parent, Method genericValueGetterMethod) {
    final Type genericReturnType = genericValueGetterMethod.getGenericReturnType();
    assert genericReturnType instanceof ParameterizedType;
    assert ((ParameterizedType) genericReturnType).getRawType().equals(GenericValue.class);

    try {
      return createControl((GenericValue)genericValueGetterMethod.invoke(parent), (Class)((ParameterizedType) genericReturnType).getActualTypeArguments()[0]);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static <T extends DomElement> CollectionControl<T> createCollectionControl(DomElement element, Class<T> aClass, String tagName) {
    final DomMethodsInfo methodsInfo = element.getMethodsInfo();
    final Method addMethod = methodsInfo.getCollectionAddMethod(tagName);
    final Method getMethod = methodsInfo.getCollectionGetMethod(tagName);
    return new CollectionControl<T>(element, getMethod, addMethod);
  }

}
