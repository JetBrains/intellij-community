/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
