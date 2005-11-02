/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class ConverterManager {
  private final Map<Method,Converter> myConvertersByMethod = new HashMap<Method, Converter>();
  private final Map<Class,Converter> myConvertersByClass = new HashMap<Class, Converter>();

  public ConverterManager() {
    myConvertersByClass.put(int.class, Converter.INTEGER_CONVERTER);
    myConvertersByClass.put(Integer.class, Converter.INTEGER_CONVERTER);
    myConvertersByClass.put(boolean.class, Converter.BOOLEAN_CONVERTER);
    myConvertersByClass.put(Boolean.class, Converter.BOOLEAN_CONVERTER);
    myConvertersByClass.put(String.class, Converter.EMPTY_CONVERTER);
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
    if (converter == null && Enum.class.isAssignableFrom(aClass) && NamedEnum.class.isAssignableFrom(aClass)) {
      converter = new EnumConverter(aClass);
      myConvertersByClass.put(aClass, converter);
    }
    return converter;
  }

  @NotNull
  private Converter getConverter(final Class converterClass) throws InstantiationException, IllegalAccessException {
    Converter converter = getDefaultConverter(converterClass);
    if (converter == null) {
      converter = (Converter) converterClass.newInstance();
      myConvertersByClass.put(converterClass, converter);
    }
    return converter;
  }


}
