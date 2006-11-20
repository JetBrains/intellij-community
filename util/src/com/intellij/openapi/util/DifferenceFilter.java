/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import java.lang.reflect.Field;

/**
 * @author peter
*/
public class DifferenceFilter<T> implements DefaultJDOMExternalizer.JDOMFilter {
  private final T myThisSettings;
  private final T myParentSettings;

  public DifferenceFilter(final T object, final T parentObject) {
    myThisSettings = object;
    myParentSettings = parentObject;
  }

  public boolean isAccept(Field field) {
    try {
      Object thisValue = field.get(myThisSettings);
      Object parentValue = field.get(myParentSettings);
      return !Comparing.equal(thisValue, parentValue);
    }
    catch (IllegalAccessException e) {
      return true;
    }
  }
}
