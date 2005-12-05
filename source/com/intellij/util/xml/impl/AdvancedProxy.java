/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.WeakValueHashMap;
import net.sf.cglib.proxy.AdvancedEnhancer;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Factory;
import net.sf.cglib.proxy.InvocationHandler;

import java.util.Map;

/**
 * @author peter
 */
public class AdvancedProxy {
  private static final Map<Pair<Class, Class[]>, Factory> ourFactories = new WeakValueHashMap<Pair<Class, Class[]>, Factory>();

  public static InvocationHandler getInvocationHandler(Object proxy) {
    return (InvocationHandler)((Factory) proxy).getCallback(0);
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
      final Pair<Class, Class[]> key = new Pair<Class, Class[]>(superClass, interfaces);
      Factory factory = ourFactories.get(key);
      if (factory != null) {
        return (T) factory.newInstance(factory.getClass().getConstructors()[0].getParameterTypes(), constructorArgs, new Callback[]{handler});
      }

      AdvancedEnhancer e = new AdvancedEnhancer();
      e.setSuperclass(superClass);
      e.setInterfaces(interfaces);
      e.setCallbackTypes(new Class[]{InvocationHandler.class});
      factory = (Factory)e.createClass().getConstructors()[0].newInstance(constructorArgs);
      factory.setCallback(0, handler);
      ourFactories.put(key, factory);
      return (T)factory;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
