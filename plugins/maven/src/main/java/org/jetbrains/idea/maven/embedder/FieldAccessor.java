package org.jetbrains.idea.maven.embedder;

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