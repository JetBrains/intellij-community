/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public class EnumConverter<T extends Enum> implements Converter<T>{
  private final Class<T> myType;
  private final boolean myIsNamedEnum;

  public EnumConverter(final Class<T> aClass) {
    myType = aClass;
    myIsNamedEnum = NamedEnum.class.isAssignableFrom(aClass);
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

}
