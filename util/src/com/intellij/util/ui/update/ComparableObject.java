/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.ui.update;

import com.intellij.util.ArrayUtil;

public interface ComparableObject {

  Object[] NONE = ArrayUtil.EMPTY_OBJECT_ARRAY;

  Object[] getEqualityObjects();

  class Impl implements ComparableObject {

    private Object[] myObjects;

    public Impl() {
      this(NONE);
    }

    public Impl(Object object) {
      this(new Object[] {object});
    }

    public Impl(Object[] objects) {
      myObjects = objects;
    }

    public Object[] getEqualityObjects() {
      return myObjects;
    }

    public final boolean equals(Object obj) {
      return ComparableObjectCheck.equals(this, obj);
    }

    public final int hashCode() {
      return ComparableObjectCheck.hashCode(this, super.hashCode());
    }
  }

}
