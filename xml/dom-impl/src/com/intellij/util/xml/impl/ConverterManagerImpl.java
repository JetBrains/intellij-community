/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  private final Map<Class,Converter> mySimpleConverters = new HashMap<>();

  ConverterManagerImpl() {
    mySimpleConverters.put(int.class, Converter.INTEGER_CONVERTER);
    mySimpleConverters.put(Integer.class, Converter.INTEGER_CONVERTER);
    mySimpleConverters.put(boolean.class, ResolvingConverter.BOOLEAN_CONVERTER);
    mySimpleConverters.put(Boolean.class, ResolvingConverter.BOOLEAN_CONVERTER);
    mySimpleConverters.put(String.class, Converter.EMPTY_CONVERTER);
    mySimpleConverters.put(Object.class, Converter.EMPTY_CONVERTER);
    mySimpleConverters.put(PathReference.class, PathReferenceConverter.INSTANCE);
  }

  @Override
  public void addConverter(Class clazz, Converter converter) {
    mySimpleConverters.put(clazz, converter);
  }

  @Override
  @NotNull
  public final Converter getConverterInstance(final Class<? extends Converter> converterClass) {
    Converter converter = getInstance(converterClass);
    assert converter != null: "Converter not found for " + converterClass;
    return converter;
  }

  <T> T getInstance(Class<T> clazz) {
    return (T)myConverterInstances.get(clazz);
  }

  @Override
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

  @Override
  public <T extends Converter> void registerConverterImplementation(Class<T> converterInterface, T converterImpl) {
    myConverterInstances.put(converterInterface, converterImpl);
  }
}
