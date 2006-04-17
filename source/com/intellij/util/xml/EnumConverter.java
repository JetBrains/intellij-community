/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author peter
 */
public class EnumConverter<T extends Enum> implements ResolvingConverter<T>{
  private final Class<T> myType;

  public EnumConverter(final Class<T> aClass) {
    myType = aClass;
  }

  private String getStringValue(final T anEnum) {
    return NamedEnumUtil.getEnumValueByElement(anEnum);
  }

  public final T fromString(final String s, final ConvertContext context) {
    return (T)NamedEnumUtil.getEnumElementByValue((Class)myType, s);
  }

  public final String toString(final T t, final ConvertContext context) {
    return getStringValue(t);
  }

  public Collection<T> getVariants(final ConvertContext context) {
    return Arrays.asList(myType.getEnumConstants());
  }
}
