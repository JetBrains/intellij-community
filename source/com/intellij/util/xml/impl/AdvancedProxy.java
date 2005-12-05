/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.containers.WeakValueHashMap;
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
  private static final Map<Class, Class> ourProxyClasses = new HashMap<Class, Class>();
  private static final Map<Object, InvocationHandler> ourInvocationHandlers = new WeakValueHashMap<Object, InvocationHandler>();

  public static InvocationHandler getInvocationHandler(Object proxy) {
    return ourInvocationHandlers.get(proxy);
  }

  public static <T>T createProxy(final Class<T> aClass,
                                 final InvocationHandler handler,
                                 final Object... constructorArgs) {
    try {
      Class clazz = getOrCreateProxyClass(aClass);
      Enhancer.registerCallbacks(clazz, new Callback[]{ handler, null });
      final T t = (T)clazz.getConstructors()[0].newInstance(constructorArgs);
      ourInvocationHandlers.put(t, handler);
      return t;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new CodeGenerationException(e);
    }
  }

  private static <T>Class getOrCreateProxyClass(final Class<T> aClass) {
    Class proxyClass = ourProxyClasses.get(aClass);
    if (proxyClass == null) {
      proxyClass = getProxyClass(aClass);
      ourProxyClasses.put(aClass, proxyClass);
    }
    return proxyClass;
  }

  private static Class getProxyClass(final Class superClass) {
    AdvancedEnhancer e = new AdvancedEnhancer();
    if (!superClass.isInterface()) {
      e.setSuperclass(superClass);
    } else {
      e.setInterfaces(new Class[]{superClass});
    }
    e.setCallbackTypes(new Class[]{InvocationHandler.class});
    e.setUseFactory(false);

    return e.createClass();
  }


}
