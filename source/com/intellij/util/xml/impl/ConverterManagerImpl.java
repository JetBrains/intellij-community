/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.*;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class ConverterManagerImpl implements ConverterManager {
  private final Map<Method, Converter> myConvertersByMethod = new HashMap<Method, Converter>();
  private final Map<Class,Converter> myConvertersByClass = new HashMap<Class, Converter>();

  public ConverterManagerImpl() {
    registerConverter(int.class, Converter.INTEGER_CONVERTER);
    registerConverter(Integer.class, Converter.INTEGER_CONVERTER);
    registerConverter(boolean.class, Converter.BOOLEAN_CONVERTER);
    registerConverter(Boolean.class, Converter.BOOLEAN_CONVERTER);
    registerConverter(String.class, Converter.EMPTY_CONVERTER);
    registerConverter(PsiClass.class, Converter.PSI_CLASS_CONVERTER);
  }

  @NotNull
  final Converter getConverter(Method method, final boolean getter) throws IllegalAccessException, InstantiationException {
    Converter converter = myConvertersByMethod.get(method);
    if (converter != null) {
      return converter;
    }

    final Convert convert = method.getAnnotation(Convert.class);
    if (convert != null) {
      converter = getConverter(convert.value());
    }
    else {
      final Class<?> aClass = getter ? method.getReturnType() : method.getParameterTypes()[0];
      converter = getDefaultConverter(aClass);
      assert converter != null: "No converter specified: String<->" + aClass.getName();
    }
    myConvertersByMethod.put(method, converter);
    return converter;
  }

  @Nullable
  private Converter getDefaultConverter(final Class aClass) {
    Converter converter = myConvertersByClass.get(aClass);
    if (converter == null && Enum.class.isAssignableFrom(aClass)) {
      converter = new EnumConverter(aClass);
      registerConverter(aClass, converter);
    }
    return converter;
  }

  @NotNull
  public final Converter getConverter(final Class converterClass) throws InstantiationException, IllegalAccessException {
    Converter converter = getDefaultConverter(converterClass);
    if (converter == null) {
      converter = (Converter) converterClass.newInstance();
      registerConverter(converterClass, converter);
    }
    return converter;
  }

  public final void registerConverter(final Class converterClass, final Converter converter) {
    myConvertersByClass.put(converterClass, converter);
  }


}
