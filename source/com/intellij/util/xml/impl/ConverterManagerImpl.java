/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.*;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class ConverterManagerImpl implements ConverterManager {
  private final Map<Pair<Type,Method>, Converter> myConvertersByMethod = new HashMap<Pair<Type, Method>, Converter>();
  private final Map<Class,Converter> myConvertersByClass = new HashMap<Class, Converter>();

  public ConverterManagerImpl() {
    registerConverter(int.class, Converter.INTEGER_CONVERTER);
    registerConverter(Integer.class, Converter.INTEGER_CONVERTER);
    registerConverter(boolean.class, ResolvingConverter.BOOLEAN_CONVERTER);
    registerConverter(Boolean.class, ResolvingConverter.BOOLEAN_CONVERTER);
    registerConverter(String.class, Converter.EMPTY_CONVERTER);
    registerConverter(PsiClass.class, Converter.PSI_CLASS_CONVERTER);
    registerConverter(PsiType.class, Converter.PSI_TYPE_CONVERTER);
  }

  final Converter getConverter(Method method, final boolean getter, Type classType, Converter genericConverter) throws IllegalAccessException, InstantiationException {
    final Pair<Type, Method> pair = new Pair<Type, Method>(classType, method);
    Converter converter = myConvertersByMethod.get(pair);
    if (converter != null) {
      return converter;
    }

    final Convert convert = DomUtil.findAnnotationDFS(method, Convert.class);
    if (convert != null) {
      converter = getConverter(convert.value());
    }
    else {
      converter = _getConverter(method, getter, classType, genericConverter);
    }
    myConvertersByMethod.put(pair, converter);
    return converter;
  }

  private Converter _getConverter(final Method method, final boolean getter, final Type classType, Converter genericConverter) {
    final Converter converter;
    Class<?> aClass = DomUtil.getClassFromGenericType(getter ? method.getGenericReturnType() : method.getGenericParameterTypes()[0], classType);
    if (aClass == null) {
      aClass = getter ? method.getReturnType() : method.getParameterTypes()[0];
    } else if (genericConverter != null) {
      return genericConverter;
    }
    converter = getDefaultConverter(aClass);
    assert converter != null: "No converter specified: String<->" + aClass.getName();
    return converter;
  }

  @Nullable
  private Converter getDefaultConverter(final Class aClass) {
    Converter converter = myConvertersByClass.get(aClass);
    if (converter == null) {
      if (Enum.class.isAssignableFrom(aClass)) {
        converter = new EnumConverter(aClass);
        registerConverter(aClass, converter);
      } else if (DomElement.class.isAssignableFrom(aClass)) {
        converter = new DomResolveConverter(aClass);
        registerConverter(aClass, converter);
      }
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
