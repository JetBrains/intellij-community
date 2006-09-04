/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.ui.update;

import com.intellij.openapi.diagnostic.Logger;


public class ComparableObjectCheck {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.update.ComparableObjectCheck");

  public static boolean equals(ComparableObject me, Object him) {
    LOG.assertTrue(me != null);

    if (me == him) {
      return true;
    }

    else if (!(him instanceof ComparableObject)) {
      return false;
    }

    Object[] my = me.getEqualityObjects();
    Object[] his = ((ComparableObject) him).getEqualityObjects();

    if (my.length == 0 || his.length == 0 || my.length != his.length) {
      return false;
    }

    for (int i = 0; i < my.length; i++) {
      if (!equals(my[i], his[i])) {
        return false;
      }
    }

    return true;
  }

  public static boolean equals(Object object1, Object object2) {
    if (object1 == object2) {
      return true;
    }
    if ((object1 == null) || (object2 == null)) {
      return false;
    }
    return object1.equals(object2);
  }
  
  public static int hashCode(ComparableObject me, int superCode) {
    Object[] objects = me.getEqualityObjects();
    if (objects.length == 0) {
      return superCode;
    }

    int result = 0;
    for (Object object : objects) {
      result = 29 * result + (object != null ? object.hashCode() : 239);
    }
    return result;
  }

}
