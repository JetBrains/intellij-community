/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.containers.WeakValueHashMap;
import net.sf.cglib.proxy.AdvancedEnhancer;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Factory;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

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
      return (T) createProxy(null, new Class[]{superClassOrInterface}, handler, Collections.EMPTY_SET);
    }
    return (T) createProxy(superClassOrInterface, (Class[])null, handler, Collections.EMPTY_SET);
  }

  public static <T> T createProxy(final Class<T> superClass,
                                  final Class[] interfaces,
                                  final InvocationHandler handler,
                                  final Set<MethodSignature> additionalMethods,
                                  final Object... constructorArgs) {
    try {
      final ProxyDescription key = new ProxyDescription(superClass, interfaces, additionalMethods);
      Factory factory = ourFactories.get(key);
      if (factory != null) {
        final Class<? extends Factory> aClass = factory.getClass();
        return (T) factory.newInstance(findConstructor(aClass, constructorArgs).getParameterTypes(), constructorArgs, new Callback[]{handler});
      }

      AdvancedEnhancer e = new AdvancedEnhancer();
      e.setAdditionalMethods(additionalMethods);
      e.setSuperclass(superClass);
      e.setInterfaces(interfaces);
      e.setCallbackTypes(new Class[]{InvocationHandler.class});
      factory = (Factory)findConstructor(e.createClass(), constructorArgs).newInstance(constructorArgs);
      factory.setCallback(0, handler);
      ourFactories.put(key, factory);
      return (T)factory;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private static Constructor findConstructor(final Class aClass, final Object... constructorArgs) {
    loop: for (final Constructor constructor : aClass.getConstructors()) {
      final Class[] parameterTypes = constructor.getParameterTypes();
      if (parameterTypes.length == constructorArgs.length) {
        for (int i = 0; i < parameterTypes.length; i++) {
          Class parameterType = parameterTypes[i];
          final Object constructorArg = constructorArgs[i];
          if (!parameterType.isInstance(constructorArg) && constructorArg != null) {
            continue loop;
          }
        }
        return constructor;
      }
    }
    throw new AssertionError("Cannot find constructor for arguments: " + Arrays.asList(constructorArgs));
  }

  private static class ProxyDescription {
    private final Class mySuperClass;
    private final Class[] myInterfaces;
    private final Set<MethodSignature> myAdditionalMethods;

    public ProxyDescription(final Class superClass, final Class[] interfaces, final Set<MethodSignature> additionalMethods) {
      mySuperClass = superClass;
      myInterfaces = interfaces;
      myAdditionalMethods = additionalMethods;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ProxyDescription that = (ProxyDescription)o;

      if (myAdditionalMethods != null ? !myAdditionalMethods.equals(that.myAdditionalMethods) : that.myAdditionalMethods != null) return false;
      if (!Arrays.equals(myInterfaces, that.myInterfaces)) return false;
      if (mySuperClass != null ? !mySuperClass.equals(that.mySuperClass) : that.mySuperClass != null) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = (mySuperClass != null ? mySuperClass.hashCode() : 0);
      result = 29 * result + (myAdditionalMethods != null ? myAdditionalMethods.hashCode() : 0);
      return result;
    }
  }

}
