/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.facade.embedder;

import java.lang.reflect.Field;

public class FieldAccessor<FIELD_TYPE> {
  private volatile FIELD_TYPE myWagonManagerCache;
  private final Class myHostClass;
  private final Object myHost;
  private final String myFieldName;

  public <T> FieldAccessor(Class<? super T> hostClass, T host, String fieldName) {
    myHostClass = hostClass;
    myHost = host;
    myFieldName = fieldName;
  }

  public FIELD_TYPE getField() {
    if (myWagonManagerCache == null) {
      Object wagon = getFieldValue(myHostClass, myFieldName, myHost);
      myWagonManagerCache = (FIELD_TYPE)wagon;
    }
    return myWagonManagerCache;
  }

  private Object getFieldValue(Class c, String fieldName, Object o) {
    try {
      Field f = c.getDeclaredField(fieldName);
      f.setAccessible(true);
      return f.get(o);
    }
    catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}