/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Comparing;

/**
 * @author peter
 */
public class NamedEnumUtil {

  public static <T extends Enum&NamedEnum> T getEnumElementByValue(final Class<T> enumClass, final String value) {
    for (final T t : enumClass.getEnumConstants()) {
      if (Comparing.equal(t.getValue(), value)) {
        return t;
      }
    }
    return null;
  }

}
