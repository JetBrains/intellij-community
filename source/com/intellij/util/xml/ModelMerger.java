/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xml.impl.AdvancedProxy;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author peter
 */
public class ModelMerger {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.ModelMerger");

  public static <T> T mergeModels(final Class<? extends T> aClass, final T... implementations) {
    return AdvancedProxy.createProxy(aClass, new MergingInvocationHandler<T>(implementations));
  }

  public static class MergingInvocationHandler<T> implements InvocationHandler {
    private final T[] myImplementations;

    public MergingInvocationHandler(final T... implementations) {
      myImplementations = implementations;
    }

    private final Object getPrimaryKey(Object implementation) throws IllegalAccessException, InvocationTargetException {
      return getPrimaryKey(implementation.getClass(), implementation);
    }

    private Object getPrimaryKey(final Class aClass, final Object implementation) throws IllegalAccessException, InvocationTargetException {
      for (final Method method : aClass.getMethods()) {
        final Class<?> returnType = method.getReturnType();
        if (isPrimaryKey(method, returnType)) {
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

    private boolean isPrimaryKey(final Method method, final Class<?> returnType) {
      return DomUtil.findAnnotationDFS(method, PrimaryKey.class) != null && returnType != void.class &&
             method.getParameterTypes().length == 0;
    }


    public final Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
      if (Object.class.equals(method.getDeclaringClass())) {
        @NonNls String methodName = method.getName();
        if ("toString".equals(methodName)) {
          return "Merger: " + Arrays.asList(myImplementations);
        }
        if ("hashCode".equals(methodName)) {
          return System.identityHashCode(proxy);
        }
        if ("equals".equals(methodName)) {
          return proxy == args[0];
        }
        return null;
      }

      final Class returnType = method.getReturnType();
      if (Collection.class.isAssignableFrom(returnType)) {
        return getMergedImplementations(method, args,
                                        DomUtil.getRawType(DomUtil.extractCollectionElementType(method.getGenericReturnType())));
      }

      if (GenericValue.class.isAssignableFrom(returnType)) {
        return new ReadOnlyGenericValue() {
          public Object getValue() {
            for (final T t : myImplementations) {
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
        for (final T t : myImplementations) {
          method.invoke(t, args);
        }
        return null;
      }

      List<Object> results = getMergedImplementations(method, args, method.getReturnType());

      return results.isEmpty() ? null : results.get(0);
    }

    private List<Object> getMergedImplementations(final Method method, final Object[] args, final Class returnType)
      throws IllegalAccessException, InvocationTargetException {

      final List<Object> results = new ArrayList<Object>();

      if (returnType.isInterface() && !GenericValue.class.isAssignableFrom(returnType)) {
        final List<Object> orderedPrimaryKeys = new ArrayList<Object>();
        final Map<Object, List<Object>> map = new HashMap<Object, List<Object>>();
        for (final T t : myImplementations) {
          final Object o = method.invoke(t, args);
          if (o instanceof Collection) {
            for (final Object o1 : (Collection)o) {
              addToMaps(o1, orderedPrimaryKeys, map, results, false);
            }
          }
          else if (o != null) {
            addToMaps(o, orderedPrimaryKeys, map, results, true);
          }

        }

        for (final Object primaryKey : orderedPrimaryKeys) {
          final List<Object> objects = map.get(primaryKey);
          if (objects.size() == 1) {
            results.add(objects.get(0));
          }
          else {
            results.add(mergeImplementations(returnType, objects.toArray()));
          }
        }
      }
      else {
        for (final T t : myImplementations) {
          final Object o = method.invoke(t, args);
          if (o instanceof Collection) {
            results.addAll((Collection)o);
          }
          else if (o != null) {
            results.add(o);
            break;
          }
        }
      }
      return results;
    }

    protected Object mergeImplementations(final Class returnType, final Object[] implementations) {
      return mergeModels(returnType, implementations);
    }

    private boolean addToMaps(final Object o,
                              final List<Object> orderedPrimaryKeys,
                              final Map<Object, List<Object>> map,
                              final List<Object> results,
                              final boolean mergeIfPKNull) throws IllegalAccessException, InvocationTargetException {
      final Object primaryKey = getPrimaryKey(o);
      if (primaryKey != null || mergeIfPKNull) {
        List<Object> list;
        if (!map.containsKey(primaryKey)) {
          orderedPrimaryKeys.add(primaryKey);
          list = new ArrayList<Object>();
          map.put(primaryKey, list);
        }
        else {
          list = map.get(primaryKey);
        }
        list.add(o);
        return false;
      }

      results.add(o);
      return true;
    }
  }
}
