/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class ConverterManagerImpl {
  private final Map<Class<? extends Converter>,Converter> myConverterInstances = new HashMap<Class<? extends Converter>, Converter>();
  private final Map<Class,Class<? extends Converter>> myConverterClasses = new HashMap<Class, Class<? extends Converter>>();

  public ConverterManagerImpl() {
    registerConverter(int.class, Converter.INTEGER_CONVERTER);
    registerConverter(Integer.class, Converter.INTEGER_CONVERTER);
    registerConverter(boolean.class, ResolvingConverter.BOOLEAN_CONVERTER);
    registerConverter(Boolean.class, ResolvingConverter.BOOLEAN_CONVERTER);
    registerConverter(String.class, Converter.EMPTY_CONVERTER);
    registerConverter(PsiClass.class, Converter.PSI_CLASS_CONVERTER);
    registerConverter(PsiType.class, Converter.PSI_TYPE_CONVERTER);
  }

  @NotNull
  final Converter getConverter(Method method, Class aClass, Converter genericConverter) throws IllegalAccessException, InstantiationException {
    final Resolve resolveAnnotation = DomUtil.findAnnotationDFS(method, Resolve.class);
    if (resolveAnnotation != null) {
      return new DomResolveConverter(resolveAnnotation.value());
    }

    final Convert convertAnnotation = DomUtil.findAnnotationDFS(method, Convert.class);
    if (convertAnnotation != null) {
      return getConverterInstance(convertAnnotation.value());
    }

    final Converter converter = getConverter(aClass);
    if (converter != null) {
      return converter;
    }

    assert genericConverter != null: "No converter specified: String<->" + aClass.getName();
    return genericConverter;
  }

  @NotNull
  public final Converter getConverterInstance(final Class<? extends Converter> converterClass) throws InstantiationException, IllegalAccessException {
    Converter converter = myConverterInstances.get(converterClass);
    if (converter == null) {
      converter = converterClass.newInstance();
      myConverterInstances.put(converterClass, converter);
    }
    return converter;
  }

  @Nullable
  public final Converter getConverter(final Class<?> aClass) throws InstantiationException, IllegalAccessException {
    final Class<? extends Converter> converterClass = myConverterClasses.get(aClass);
    if (converterClass != null) {
      return getConverterInstance(converterClass);
    }
    if (Enum.class.isAssignableFrom(aClass)) {
      return new EnumConverter(aClass);
    }
    if (DomElement.class.isAssignableFrom(aClass)) {
      return new DomResolveConverter(aClass);
    }
    return null;
  }

  public final void registerConverter(final Class aClass, final Converter converter) {
    myConverterClasses.put(aClass, converter.getClass());
    myConverterInstances.put(converter.getClass(), converter);
  }


}
