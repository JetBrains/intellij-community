/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import net.sf.cglib.proxy.InvocationHandler;
import net.sf.cglib.proxy.Proxy;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class ModelMerger {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.ModelMerger");

  public static <T> T mergeModels(final Class<T> aClass, final T... implementations) {
    final InvocationHandler invocationHandler = new InvocationHandler() {
      public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
        if (method.getDeclaringClass().getName().equals("java.lang.Object")) {
          @NonNls String methodName = method.getName();
          if (methodName.equals("toString")) {
            return "Merger: " + Arrays.asList(implementations);
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

        if (List.class.isAssignableFrom(method.getReturnType())) {
          final ArrayList<Object> result = new ArrayList<Object>();
          for (final T t : implementations) {
            result.addAll((List<Object>)method.invoke(t, args));
          }
          return result;
        }

        if (GenericValue.class.isAssignableFrom(method.getReturnType())) {
          return new ReadOnlyGenericValue() {
            public Object getValue() {
              for (final T t : implementations) {
                try {
                  GenericValue genericValue = (GenericValue) method.invoke(t, args);
                  if (genericValue != null) {
                    final Object value = genericValue.getValue();
                    if (value != null) {
                      return value;
                    }
                  }
                }
                catch (IllegalAccessException e) {
                  LOG.error(e);
                }
                catch (InvocationTargetException e) {
                  LOG.error(e);
                }
              }
              return null;
            }
          };
        }

        if (void.class == method.getReturnType()) {
          for (final T t : implementations) {
            method.invoke(t, args);
          }
          return null;
        }

        for (final T t : implementations) {
          final Object o = method.invoke(t, args);
          if (o != null) return o;
        }

        return null;
      }
    };
    return (T)Proxy.newProxyInstance(null, new Class[]{aClass}, invocationHandler);
  }
}
