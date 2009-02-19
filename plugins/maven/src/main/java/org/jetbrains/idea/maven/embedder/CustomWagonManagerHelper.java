package org.jetbrains.idea.maven.embedder;

import java.lang.reflect.Field;

public class CustomWagonManagerHelper {
  private CustomWagonManager myWagonManagerCache;
  private final Class myHostClass;
  private final Object myHost;

  public <T> CustomWagonManagerHelper(Class<? super T> hostClass, T host) {
    myHostClass = hostClass;
    myHost = host;
  }

  public void open() {
    getWagonManager().open();
  }

  public void close() {
    getWagonManager().close();
  }

  private CustomWagonManager getWagonManager() {
    if (myWagonManagerCache == null) {
      Object wagon = getFieldValue(myHostClass, "wagonManager", myHost);
      myWagonManagerCache = (CustomWagonManager)wagon;
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
