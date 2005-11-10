/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class EnumConverter<T extends Enum> implements Converter<T>{
  private final boolean myIsNamedEnum;
  private final Map<String,T> myCachedElements = new HashMap<String, T>();

  public EnumConverter(final Class<T> aClass) {
    myIsNamedEnum = NamedEnum.class.isAssignableFrom(aClass);
    for (T anEnum : aClass.getEnumConstants()) {
      myCachedElements.put(getValue(anEnum), anEnum);
    }
  }

  private String getValue(final T anEnum) {
    return myIsNamedEnum ? ((NamedEnum)anEnum).getValue() : anEnum.name();
  }

  public T fromString(final String s, final ConvertContext context) {
    return myCachedElements.get(s);
  }

  public String toString(final T t, final ConvertContext context) {
    return getValue(t);
  }
}
