/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xml.impl.AdvancedProxy;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author peter
 */
public class ModelMerger {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.ModelMerger");

  public interface MergedObject {
    public <T> T findImplementation(Class<T> clazz);
  }


  public static <T> T mergeModels(final Class<? extends T> aClass, final T... implementations) {
    return AdvancedProxy.createProxy(new MergingInvocationHandler<T>(implementations), aClass, MergedObject.class);
  }

  @Nullable
  public static <T> T getImplementation(Object element, Class<T> clazz) {
    return element instanceof ModelMerger.MergedObject ?
           ((ModelMerger.MergedObject)element).findImplementation(clazz) :
           element != null && clazz.isAssignableFrom(element.getClass())? (T)element: null;
  }

  public static class MergingInvocationHandler<T> implements InvocationHandler {
    private static final Map<Class,Method> ourPrimaryKeyMethods = new HashMap<Class, Method>();
    private T[] myImplementations;

    public MergingInvocationHandler(final T... implementations) {
      setImplementations(implementations);
    }

    public MergingInvocationHandler() {
    }

    protected final void setImplementations(final T[] implementations) {
      myImplementations = implementations;
    }

    protected Object getPrimaryKey(Object implementation) throws IllegalAccessException, InvocationTargetException {
      if (implementation instanceof GenericValue) return ((GenericValue)implementation).getValue();
      final Method method = getPrimaryKeyMethod(implementation.getClass());
      if (method == null) return null;

      final Object o = method.invoke(implementation);
      return GenericValue.class.isAssignableFrom(method.getReturnType()) ? ((GenericValue)o).getValue() : o;
    }

    @Nullable
    private Method getPrimaryKeyMethod(final Class aClass) {
      Method method = ourPrimaryKeyMethods.get(aClass);
      if (method == null) {
        if (ourPrimaryKeyMethods.containsKey(aClass)) return null;

        for (final Method method1 : aClass.getMethods()) {
          if ((method = findPrimaryKeyAnnotatedMethod(method1, aClass)) != null) {
            break;
          }
        }
        ourPrimaryKeyMethods.put(aClass, method);
      }
      return method;
    }

    private Method findPrimaryKeyAnnotatedMethod(final Method method, final Class aClass) {
      return method.getReturnType() != void.class
             && method.getParameterTypes().length == 0?
             JavaMethodSignature.getSignature(method).findAnnotatedMethod(PrimaryKey.class, aClass): null;
    }


    public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
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

      try {
        if (MergedObject.class.equals(method.getDeclaringClass())) {
          return findImplementation((Class<Object>)args[0]);
        }
        final Class returnType = method.getReturnType();
        if (Collection.class.isAssignableFrom(returnType)) {
          return getMergedImplementations(method, args,
                                          DomUtil.getRawType(DomUtil.extractCollectionElementType(method.getGenericReturnType())));
        }

        if (GenericValue.class.isAssignableFrom(returnType)) {
          return new MergedGenericValue(method, args);
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
      catch (InvocationTargetException ex) {
        throw ex.getTargetException();
      }
    }

    private List<Object> getMergedImplementations(final Method method, final Object[] args, final Class returnType)
      throws IllegalAccessException, InvocationTargetException {

      final List<Object> results = new ArrayList<Object>();

      if (returnType.isInterface() /*&& !GenericValue.class.isAssignableFrom(returnType)*/) {
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
          results.add(mergeImplementations(returnType, map.get(primaryKey).toArray()));
        }
      }
      else {
        HashSet<Object> map = new HashSet<Object>();
        for (final T t : myImplementations) {
          final Object o = method.invoke(t, args);
          if (o instanceof Collection) {
            map.addAll((Collection<Object>)o);
          }
          else if (o != null) {
            map.add(o);
            break;
          }
        }
        results.addAll(map);
      }
      return results;
    }

    protected Object mergeImplementations(final Class returnType, final Object... implementations) {
      if (implementations.length == 1) {
        return implementations[0];
      }
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

    public <V> V findImplementation(final Class<V> clazz) {
      for (final T t : myImplementations) {
        if (clazz.isAssignableFrom(t.getClass())) {
          return (V) t;
        }
      }
      return null;
    }

    public class MergedGenericValue extends ReadOnlyGenericValue implements MergedObject {
      private final Method myMethod;
      private final Object[] myArgs;

      public MergedGenericValue(final Method method, final Object[] args) {
        myMethod = method;
        myArgs = args;
      }

      public <V> V findImplementation(Class<V> clazz) {
        for (final T t : myImplementations) {
          try {
            GenericValue genericValue = (GenericValue)myMethod.invoke(t, myArgs);
            if (genericValue!=null && clazz.isAssignableFrom(genericValue.getClass())) {
              return (V) genericValue;
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

      private GenericValue findGenericValue() {
        for (final T t : myImplementations) {
          try {
            GenericValue genericValue = (GenericValue)myMethod.invoke(t, myArgs);
            if (genericValue != null) {
              final Object value = genericValue.getValue();
              if (value != null) {
                return genericValue;
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

      public Object getValue() {
        final GenericValue genericValue = findGenericValue();
        return genericValue != null ? genericValue.getValue() : null;
      }

      public String getStringValue() {
        final GenericValue genericValue = findGenericValue();
        return genericValue != null ? genericValue.getStringValue() : super.getStringValue();
      }
    }
  }
}
