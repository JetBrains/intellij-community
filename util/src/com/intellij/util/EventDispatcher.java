/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author max
 */
public class EventDispatcher <T extends EventListener>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.EventDispatcher");

  private final T myMulticaster;

  private CopyOnWriteArrayList<T> myListeners = new CopyOnWriteArrayList<T>();

  private List<T> myCachedListeners = null;

  public static <T extends EventListener> EventDispatcher<T> create(Class<T> listenerClass) {
    return new EventDispatcher<T>(listenerClass);
  }

  private EventDispatcher(Class<T> listenerClass) {
    InvocationHandler handler = new InvocationHandler() {
      @NonNls public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
        if (method.getDeclaringClass().getName().equals("java.lang.Object")) {
          @NonNls String methodName = method.getName();
          if (methodName.equals("toString")) {
            return "Multicaster";
          }
          else if (methodName.equals("hashCode")) {
            return new Integer(System.identityHashCode(proxy));
          }
          else if (methodName.equals("equals")) {
            return proxy == args[0] ? Boolean.TRUE : Boolean.FALSE;
          }
          else {
            LOG.error("Incorrect Object's method invoked for proxy:" + methodName);
            return null;
          }
        }
        else {
          dispatch(method, args);
          return null;
        }
      }

    };

    //noinspection unchecked
    myMulticaster = (T)Proxy.newProxyInstance(listenerClass.getClassLoader(),
                                              new Class[]{listenerClass},
                                              handler
    );
  }

  public T getMulticaster() {
    return myMulticaster;
  }

  private void dispatch(final Method method, final Object[] args) {
    for (T listener : myListeners) {
      try {
        method.invoke(listener, args);
      }
      catch(AbstractMethodError e) {
        //Do nothing. This listener just does not implement something newly added yet.
      }
      catch (RuntimeException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e.getCause());
      }
    }
  }

  public synchronized void addListener(T listener) {
    myListeners.add(listener);
    myCachedListeners = null;
  }

  public synchronized void addListener(final T listener, Disposable parentDisposable) {
    addListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeListener(listener);
      }
    });
  }

  public synchronized void removeListener(T listener) {
    myListeners.remove(listener);
    myCachedListeners = null;
  }

  public synchronized List<T> getListeners() {
    if (myCachedListeners == null) {
      myCachedListeners = new ArrayList<T>(myListeners);
    }
    return myCachedListeners;
  }

  public synchronized boolean hasListeners() {
    return myListeners.size() > 0;
  }
}
