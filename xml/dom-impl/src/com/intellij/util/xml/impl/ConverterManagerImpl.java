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
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ConcurrentInstanceMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.converters.PathReferenceConverter;
import com.intellij.util.xml.converters.values.NumberValueConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
public class ConverterManagerImpl implements ConverterManager {

  private final ImplementationClassCache myImplementationClassCache = new ImplementationClassCache(DomImplementationClassEP.CONVERTER_EP_NAME);

  private final ConcurrentMap<Class, Object> myConverterInstances = ConcurrentFactoryMap.createMap(key-> {
      Class implementation = myImplementationClassCache.get(key);
      return ConcurrentInstanceMap.calculate(implementation == null ? key : implementation);
    }
  );
  private final Map<Class,Converter> mySimpleConverters = new HashMap<>();

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

  protected void addConverter(Class clazz, Converter converter) {
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
}
