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
  private final Class<T> myClass;
  private final Map<String,T> myCachedElements = new HashMap<String, T>();

  public EnumConverter(final Class<T> aClass) {
    myIsNamedEnum = NamedEnum.class.isAssignableFrom(aClass);
    myClass = aClass;
    for (T anEnum : aClass.getEnumConstants()) {
      if (myIsNamedEnum) {
        myCachedElements.put(((NamedEnum)anEnum).getValue(), anEnum);
      } else {
        myCachedElements.put(anEnum.name(), anEnum);
      }
    }
  }

  public T fromString(final String s, final ConvertContext context) throws ConvertFormatException {
    final T t = myCachedElements.get(s);
    if (t == null) {
      throw new ConvertFormatException(s, myClass);
    }
    return t;
  }

  public String toString(final T t, final ConvertContext context) {
    if (myIsNamedEnum) {
      return ((NamedEnum)t).getValue();
    } else {
      return t.name();
    }
  }
}
