/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EventListener;

public class EventUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.EventUtil");

  public static <T extends EventListener> T createWeakListener(final Class<T> listenerClass, T listener) {
    final WeakReference reference = new WeakReference(listener);

    InvocationHandler handler = new InvocationHandler() {
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object o = reference.get();
        if (o == null) {
          if ("equals".equals(method.getName())) return Boolean.FALSE;
          return null;
        }

        try{
          Object result = method.invoke(o, args);
          return result;
        }
        catch(IllegalAccessException e){
          LOG.error(e);
        }
        catch(IllegalArgumentException e){
          LOG.error(e);
        }
        catch(InvocationTargetException e){
          throw e.getTargetException();
        }

        return null;
      }
    };

    return (T)Proxy.newProxyInstance(listenerClass.getClassLoader(),
      new Class[]{listenerClass},
      handler
    );
  }
}
