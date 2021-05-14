// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.openapi.paths.PathReference;
import com.intellij.util.xml.*;
import com.intellij.util.xml.converters.PathReferenceConverter;
import com.intellij.util.xml.converters.values.NumberValueConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class ConverterManagerImpl implements ConverterManager {
  private static final class MyClassValue extends ClassValue<Object> {
    private final ImplementationClassCache implementationClassCache = new ImplementationClassCache(DomImplementationClassEP.CONVERTER_EP_NAME);

    @Override
    protected Object computeValue(Class<?> key) {
      Class<?> implementation = implementationClassCache.get(key);
      Class<?> aClass = implementation == null ? key : implementation;
      try {
        Constructor<?> constructor = aClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
      }
      catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
        throw new RuntimeException("Couldn't instantiate " + aClass, e);
      }
    }
  }

  private final Map<Class<?>, Converter<?>> mySimpleConverters = new HashMap<>();

  protected ConverterManagerImpl() {
    mySimpleConverters.put(byte.class, new NumberValueConverter<>(byte.class, false));
    mySimpleConverters.put(Byte.class, new NumberValueConverter<>(Byte.class, true));

    mySimpleConverters.put(short.class, new NumberValueConverter<>(short.class, false));
    mySimpleConverters.put(Short.class, new NumberValueConverter<>(Short.class, true));

    mySimpleConverters.put(int.class, new NumberValueConverter<>(int.class, false));
    mySimpleConverters.put(Integer.class, new NumberValueConverter<>(Integer.class, false));

    mySimpleConverters.put(long.class, new NumberValueConverter<>(long.class, false));
    mySimpleConverters.put(Long.class, new NumberValueConverter<>(Long.class, true));

    mySimpleConverters.put(float.class, new NumberValueConverter<>(float.class, false));
    mySimpleConverters.put(Float.class, new NumberValueConverter<>(Float.class, true));

    mySimpleConverters.put(double.class, new NumberValueConverter<>(double.class, false));
    mySimpleConverters.put(Double.class, new NumberValueConverter<>(Double.class, true));

    mySimpleConverters.put(BigDecimal.class, new NumberValueConverter<>(BigDecimal.class, true));
    mySimpleConverters.put(BigInteger.class, new NumberValueConverter<>(BigInteger.class, true));

    mySimpleConverters.put(boolean.class, ResolvingConverter.BOOLEAN_CONVERTER);
    mySimpleConverters.put(Boolean.class, ResolvingConverter.BOOLEAN_CONVERTER);

    mySimpleConverters.put(String.class, Converter.EMPTY_CONVERTER);
    mySimpleConverters.put(Object.class, Converter.EMPTY_CONVERTER);
    mySimpleConverters.put(PathReference.class, PathReferenceConverter.INSTANCE);
  }

  protected void addConverter(Class<?> clazz, Converter<?> converter) {
    mySimpleConverters.put(clazz, converter);
  }

  @Override
  public final @NotNull Converter<?> getConverterInstance(Class<? extends Converter> converterClass) {
    Converter<?> converter = getOrCreateConverterInstance(converterClass);
    assert converter != null: "Converter not found for " + converterClass;
    return converter;
  }

  static <T> T getOrCreateConverterInstance(Class<T> clazz) {
    //noinspection unchecked
    return (T)DomImplementationClassEP.CONVERTER_EP_NAME.computeIfAbsent(ConverterManagerImpl.class, () -> new MyClassValue()).get(clazz);
  }

  @SuppressWarnings("unchecked")
  @Override
  public final @Nullable Converter<?> getConverterByClass(final Class<?> convertingClass) {
    Converter<?> converter = mySimpleConverters.get(convertingClass);
    if (converter != null) {
      return converter;
    }

    if (Enum.class.isAssignableFrom(convertingClass)) {
      return EnumConverter.createEnumConverter((Class<? extends Enum<?>>)convertingClass);
    }
    if (DomElement.class.isAssignableFrom(convertingClass)) {
      return DomResolveConverter.createConverter((Class<? extends DomElement>)convertingClass);
    }
    return null;
  }
}
