/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.containers.WeakValueHashMap;
import net.sf.cglib.proxy.AdvancedEnhancer;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Factory;
import net.sf.cglib.proxy.InvocationHandler;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

/**
 * @author peter
 */
public class AdvancedProxy {
  private static final Map<ProxyDescription, Factory> ourFactories = new WeakValueHashMap<ProxyDescription, Factory>();

  public static InvocationHandler getInvocationHandler(Object proxy) {
    return (InvocationHandler)((Factory) proxy).getCallback(0);
  }

  public static <T> T createProxy(final Class<T> superClassOrInterface, final InvocationHandler handler) {
    if (superClassOrInterface.isInterface()) {
      return createProxy((Class<T>)null, new Class[]{superClassOrInterface}, handler, new Method[0]);
    }
    return createProxy(superClassOrInterface, (Class[])null, handler, new Method[0]);
  }

  public static <T> T createProxy(final Class<T> superClass,
                                  final Class[] interfaces,
                                  final InvocationHandler handler,
                                  final Method[] additionalMethods,
                                  final Object... constructorArgs) {
    try {
      final ProxyDescription key = new ProxyDescription(superClass, interfaces, additionalMethods);
      Factory factory = ourFactories.get(key);
      if (factory != null) {
        return (T) factory.newInstance(factory.getClass().getConstructors()[0].getParameterTypes(), constructorArgs, new Callback[]{handler});
      }

      AdvancedEnhancer e = new AdvancedEnhancer();
      e.setAdditionalMethods(new HashSet<Method>(Arrays.asList(additionalMethods)));
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

  private static class ProxyDescription {
    private final Class mySuperClass;
    private final Class[] myInterfaces;
    private final Method[] myAdditionalMethods;

    public ProxyDescription(final Class superClass, final Class[] interfaces, final Method[] additionalMethods) {
      mySuperClass = superClass;
      myInterfaces = interfaces;
      myAdditionalMethods = additionalMethods;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ProxyDescription that = (ProxyDescription)o;

      if (!Arrays.equals(myAdditionalMethods, that.myAdditionalMethods)) return false;
      if (!Arrays.equals(myInterfaces, that.myInterfaces)) return false;
      if (mySuperClass != null ? !mySuperClass.equals(that.mySuperClass) : that.mySuperClass != null) return false;

      return true;
    }

    public int hashCode() {
      return (mySuperClass != null ? mySuperClass.hashCode() : 0);
    }
  }

}
