package com.intellij.util.xml.impl;

import com.intellij.openapi.paths.PathReference;
import com.intellij.util.containers.ConcurrentInstanceMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.converters.PathReferenceConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
class ConverterManagerImpl implements ConverterManager {

  private final ImplementationClassCache myImplementationClassCache = new ImplementationClassCache(DomImplementationClassEP.CONVERTER_EP_NAME);

  private final ConcurrentInstanceMap<Object> myConverterInstances = new ConcurrentInstanceMap<Object>() {
    @NotNull
    @Override
    protected Object create(Class key) {
      Class implementation = myImplementationClassCache.get(key);
      return super.create(implementation == null ? key : implementation);
    }
  };
  private final Map<Class,Converter> mySimpleConverters = new HashMap<Class, Converter>();

  ConverterManagerImpl() {
    mySimpleConverters.put(int.class, Converter.INTEGER_CONVERTER);
    mySimpleConverters.put(Integer.class, Converter.INTEGER_CONVERTER);
    mySimpleConverters.put(boolean.class, ResolvingConverter.BOOLEAN_CONVERTER);
    mySimpleConverters.put(Boolean.class, ResolvingConverter.BOOLEAN_CONVERTER);
    mySimpleConverters.put(String.class, Converter.EMPTY_CONVERTER);
    mySimpleConverters.put(Object.class, Converter.EMPTY_CONVERTER);
    mySimpleConverters.put(PathReference.class, PathReferenceConverter.INSTANCE);
  }

  public void addConverter(Class clazz, Converter converter) {
    mySimpleConverters.put(clazz, converter);
  }

  @NotNull
  public final Converter getConverterInstance(final Class<? extends Converter> converterClass) {
    Converter converter = getInstance(converterClass);
    assert converter != null: "Converter not found for " + converterClass;
    return converter;
  }

  <T> T getInstance(Class<T> clazz) {
    return (T)myConverterInstances.get(clazz);
  }

  @Nullable
  public final Converter getConverterByClass(final Class<?> convertingClass) {
    final Converter converter = mySimpleConverters.get(convertingClass);
    if (converter != null) {
      return converter;
    }

    if (Enum.class.isAssignableFrom(convertingClass)) {
      return EnumConverter.createEnumConverter((Class<? extends Enum>)convertingClass);
    }
    if (DomElement.class.isAssignableFrom(convertingClass)) {
      return DomResolveConverter.createConverter((Class<? extends DomElement>)convertingClass);
    }
    return null;
  }

  public <T extends Converter> void registerConverterImplementation(Class<T> converterInterface, T converterImpl) {
    myConverterInstances.put(converterInterface, converterImpl);
  }
}
