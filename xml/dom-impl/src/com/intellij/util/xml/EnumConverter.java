// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
public final class EnumConverter<T extends Enum> extends ResolvingConverter<T>{
  private static final ConcurrentMap<Class, EnumConverter> ourCache =
    ConcurrentFactoryMap.createMap(key -> new EnumConverter(key));
  private final Class<T> myType;

  private EnumConverter(final Class<T> aClass) {
    myType = aClass;
  }

  public static <T extends Enum> EnumConverter<T>  createEnumConverter(Class<T> aClass) {
    return ourCache.get(aClass);
  }

  private String getStringValue(final T anEnum) {
    return NamedEnumUtil.getEnumValueByElement(anEnum);
  }

  @Override
  public final T fromString(final String s, final ConvertContext context) {
    return s==null?null:(T)NamedEnumUtil.getEnumElementByValue((Class)myType, s);
  }

  @Override
  public final String toString(final T t, final ConvertContext context) {
    return t == null? null:getStringValue(t);
  }

  @Override
  public String getErrorMessage(@Nullable final String s, final ConvertContext context) {
    return XmlDomBundle.message("error.unknown.enum.value.message", s);
  }

  @Override
  @NotNull
  public Collection<? extends T> getVariants(final ConvertContext context) {
    final XmlElement element = context.getXmlElement();
    if (element instanceof XmlTag) {
      final XmlTag simpleContent = XmlUtil.getSchemaSimpleContent((XmlTag)element);
      if (simpleContent != null && XmlUtil.collectEnumerationValues(simpleContent, new HashSet<>())) {
        return Collections.emptyList();
      }
    }
    return Arrays.asList(myType.getEnumConstants());
  }

  public boolean isExhaustive() {
    return !ReflectionUtil.isAssignable(NonExhaustiveEnum.class, myType);
  }
}
