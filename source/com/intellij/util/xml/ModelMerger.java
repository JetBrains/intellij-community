/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import net.sf.cglib.proxy.InvocationHandler;
import net.sf.cglib.proxy.Proxy;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author peter
 */
public class ModelMerger {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.ModelMerger");
  private static final Object MERGE_ALL_KEY = new Object();

  public static <T> T mergeModels(final Class<? extends T> aClass, final T... implementations) {
    final InvocationHandler invocationHandler = new InvocationHandler() {
      private final Object getPrimaryKey(Object implementation) throws IllegalAccessException, InvocationTargetException {
        final Class<? extends Object> aClass = implementation.getClass();
        return getPrimaryKey(aClass, implementation);
      }

      private Object getPrimaryKey(final Class aClass, final Object implementation)
        throws IllegalAccessException, InvocationTargetException {
        if (aClass.isAnnotationPresent(PrimaryKey.class)) {
          return MERGE_ALL_KEY;
        }

        for (final Method method : aClass.getMethods()) {
          final Class<?> returnType = method.getReturnType();
          if (method.isAnnotationPresent(PrimaryKey.class) && returnType != void.class && method.getParameterTypes().length == 0) {
            final Object o = method.invoke(implementation);
            return GenericValue.class.isAssignableFrom(returnType) ? ((GenericValue)o).getValue() : o;
          }
        }

        for (final Class aClass1 : aClass.getInterfaces()) {
          final Object primaryKey = getPrimaryKey(aClass1, implementation);
          if (primaryKey != null) return primaryKey;
        }

        return null;
      }


      public final Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
        if (method.getDeclaringClass().getName().equals("java.lang.Object")) {
          @NonNls String methodName = method.getName();
          if (methodName.equals("toString")) {
            return "Merger: " + Arrays.asList(implementations);
          }
          else if (methodName.equals("hashCode")) {
            return System.identityHashCode(proxy);
          }
          else if (methodName.equals("equals")) {
            return proxy == args[0];
          }
          else {
            LOG.error("Incorrect Object's method invoked for proxy:" + methodName);
            return null;
          }
        }

        final Class returnType = method.getReturnType();
        if (List.class.isAssignableFrom(returnType)) {
          return getMergedImplementations(method, args, true);
        }

        if (GenericValue.class.isAssignableFrom(returnType)) {
          return new ReadOnlyGenericValue() {
            public Object getValue() {
              for (final T t : implementations) {
                try {
                  GenericValue genericValue = (GenericValue)method.invoke(t, args);
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

        if (void.class == returnType) {
          for (final T t : implementations) {
            method.invoke(t, args);
          }
          return null;
        }

        List<Object> results = getMergedImplementations(method, args, false);

        return results.isEmpty() ? null : results.get(0);
      }

      private List<Object> getMergedImplementations(final Method method, final Object[] args, final boolean isList)
        throws IllegalAccessException,
               InvocationTargetException {
        Set<Object> seenPrimaryKeys = new HashSet<Object>();
        List<Object> orderedPrimaryKeys = new ArrayList<Object>();
        Map<Object, List<Object>> map = new HashMap<Object, List<Object>>();
        List<Object> results = new ArrayList<Object>();
        for (final T t : implementations) {
          if (isList || results.isEmpty()) {
            final Object o = method.invoke(t, args);
            if (isList) {
              for (final Object o1 : (List)o) {
                addToMaps(o1, seenPrimaryKeys, orderedPrimaryKeys, map, results);
              }
            }
            else if (o != null) {
              addToMaps(o, seenPrimaryKeys, orderedPrimaryKeys, map, results);
            }
          }
        }

        final Class<?> returnType =
          isList ? DomUtil.getRawType(DomUtil.extractCollectionElementType(method.getGenericReturnType())) : method.getReturnType();
        for (final Object primaryKey : orderedPrimaryKeys) {
          final List<Object> objects = map.get(primaryKey);
          if (objects.size() == 1) {
            results.add(objects.get(0));
          }
          else {
            results.add(mergeModels(returnType, objects.toArray()));
          }
        }
        return results;
      }

      private void addToMaps(final Object o,
                             final Set<Object> seenPrimaryKeys,
                             final List<Object> orderedPrimaryKeys,
                             final Map<Object, List<Object>> map, final List<Object> results) throws IllegalAccessException,
                                                                                                     InvocationTargetException {
        final Object primaryKey = getPrimaryKey(o);
        if (primaryKey != null) {
          List<Object> list;
          if (!seenPrimaryKeys.contains(primaryKey)) {
            seenPrimaryKeys.add(primaryKey);
            orderedPrimaryKeys.add(primaryKey);
            list = new ArrayList<Object>();
            map.put(primaryKey, list);
          }
          else {
            list = map.get(primaryKey);
          }
          list.add(o);
        }
        else {
          results.add(o);
        }
      }
    };
    return (T)Proxy.newProxyInstance(null, new Class[]{aClass}, invocationHandler);
  }


}
