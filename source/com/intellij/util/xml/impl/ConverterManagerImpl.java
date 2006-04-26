/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
class ConverterManagerImpl {
  private final FactoryMap<Class<? extends Converter>,Converter> myConverterInstances = new FactoryMap<Class<? extends Converter>, Converter>() {
    @NotNull
    protected Converter create(final Class<? extends Converter> key) {
      try {
        return key.newInstance();
      }
      catch (InstantiationException e) {
        throw new RuntimeException(e);
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  };
  private final Map<Class,Converter> mySimpleConverters = new HashMap<Class, Converter>();

  ConverterManagerImpl() {
    mySimpleConverters.put(int.class, Converter.INTEGER_CONVERTER);
    mySimpleConverters.put(Integer.class, Converter.INTEGER_CONVERTER);
    mySimpleConverters.put(boolean.class, ResolvingConverter.BOOLEAN_CONVERTER);
    mySimpleConverters.put(Boolean.class, ResolvingConverter.BOOLEAN_CONVERTER);
    mySimpleConverters.put(String.class, Converter.EMPTY_CONVERTER);
    mySimpleConverters.put(PsiClass.class, Converter.PSI_CLASS_CONVERTER);
    mySimpleConverters.put(PsiType.class, Converter.PSI_TYPE_CONVERTER);
  }

  @NotNull
  final Converter getConverter(Method method, Class aClass, Converter genericConverter) throws IllegalAccessException, InstantiationException {
    final Resolve resolveAnnotation = DomUtil.findAnnotationDFS(method, Resolve.class);
    if (resolveAnnotation != null) {
      return DomResolveConverter.createConverter(resolveAnnotation.value());
    }

    final Convert convertAnnotation = DomUtil.findAnnotationDFS(method, Convert.class);
    if (convertAnnotation != null) {
      return getConverterInstance(convertAnnotation.value());
    }
    if (genericConverter != null) {
      return genericConverter;
    }
    final Converter converter = getConverter(aClass);
    //if (converter != null) {
    //  return converter;
    //}
    assert converter != null : "No converter specified: String<->" + aClass.getName();
    return converter;

    //assert genericConverter != null: "No converter specified: String<->" + aClass.getName();
    //return genericConverter;
  }

  @NotNull
  private Converter getConverterInstance(final Class<? extends Converter> converterClass) throws InstantiationException, IllegalAccessException {
    return myConverterInstances.get(converterClass);
  }

  @Nullable
  final Converter getConverter(final Class<?> aClass) throws InstantiationException, IllegalAccessException {
    final Converter converter = mySimpleConverters.get(aClass);
    if (converter != null) {
      return converter;
    }

    if (Enum.class.isAssignableFrom(aClass)) {
      return EnumConverter.createEnumConverter((Class<? extends Enum>)aClass);
    }
    if (DomElement.class.isAssignableFrom(aClass)) {
      return DomResolveConverter.createConverter((Class<? extends DomElement>)aClass);
    }
    return null;
  }


}
