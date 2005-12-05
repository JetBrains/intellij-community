/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.containers.WeakValueHashMap;
import com.intellij.openapi.util.Pair;
import net.sf.cglib.core.CodeGenerationException;
import net.sf.cglib.proxy.AdvancedEnhancer;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InvocationHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class AdvancedProxy {
  private static final Map<Pair<Class, Class[]>, Class> ourProxyClasses = new HashMap<Pair<Class, Class[]>, Class>();
  private static final Map<Object, InvocationHandler> ourInvocationHandlers = new WeakValueHashMap<Object, InvocationHandler>();

  public static InvocationHandler getInvocationHandler(Object proxy) {
    return ourInvocationHandlers.get(proxy);
  }

  public static <T> T createProxy(final Class<T> superClass,
                                 final InvocationHandler handler,
                                 final Object... constructorArgs) {
    if (superClass.isInterface()) {
      return (T) createProxy(null, new Class[]{superClass}, handler, constructorArgs);
    }
    return (T) createProxy(superClass, (Class[])null, handler, constructorArgs);
  }

  public static <T> T createProxy(final Class<T> superClass,
                                  final Class[] interfaces,
                                 final InvocationHandler handler,
                                 final Object... constructorArgs) {
    try {
      Class clazz = getOrCreateProxyClass(superClass, interfaces);
      Enhancer.registerStaticCallbacks(clazz, new Callback[]{ handler, null });
      final T t = (T)clazz.getConstructors()[0].newInstance(constructorArgs);
      ourInvocationHandlers.put(t, handler);
      return t;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new CodeGenerationException(e);
    }
  }

  private static <T>Class getOrCreateProxyClass(final Class<T> superClass, final Class... interfaces) {
    final Pair<Class, Class[]> key = new Pair<Class, Class[]>(superClass, interfaces);
    Class proxyClass = ourProxyClasses.get(key);
    if (proxyClass == null) {
      proxyClass = getProxyClass(superClass, interfaces);
      ourProxyClasses.put(key, proxyClass);
    }
    return proxyClass;
  }

  private static Class getProxyClass(final Class superClass, final Class... interfaces) {
    AdvancedEnhancer e = new AdvancedEnhancer();
    e.setSuperclass(superClass);
    e.setInterfaces(interfaces);
    e.setCallbackTypes(new Class[]{InvocationHandler.class});
    e.setUseFactory(false);

    return e.createClass();
  }


}
