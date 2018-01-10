/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Pair;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import gnu.trove.THashSet;
import net.sf.cglib.proxy.AdvancedProxy;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
public class ModelMergerImpl implements ModelMerger {
  private final List<Pair<InvocationStrategy,Class>> myInvocationStrategies = new ArrayList<>();
  private final List<MergingStrategy> myMergingStrategies = new ArrayList<>();
  private final List<Class> myMergingStrategyClasses = new ArrayList<>();
  private static final Class<MergedObject> MERGED_OBJECT_CLASS = MergedObject.class;

  private final ConcurrentMap<Method,List<Pair<InvocationStrategy,Class>>> myAcceptsCache = ConcurrentFactoryMap.createMap(method-> {
      List<Pair<InvocationStrategy,Class>> result = new ArrayList<>();
      for (int i = myInvocationStrategies.size() - 1; i >= 0; i--) {
        final Pair<InvocationStrategy, Class> pair = myInvocationStrategies.get(i);
        if (pair.first.accepts(method)) {
          result.add(pair);
        }
      }
      return result;
    }
  );

  public ModelMergerImpl() {
    addInvocationStrategy(Object.class, new InvocationStrategy<Object>() {
      @Override
      public boolean accepts(final Method method) {
        return true;
      }

      @Override
      public Object invokeMethod(final JavaMethod javaMethod, final Object proxy, final Object[] args, final List<Object> implementations)
        throws IllegalAccessException, InvocationTargetException {
        final Method method = javaMethod.getMethod();
        List<Object> results = getMergedImplementations(method, args, method.getReturnType(), implementations, isIntersectionMethod(javaMethod));
        return results.isEmpty() ? null : results.get(0);
      }
    });

    addInvocationStrategy(Object.class, new InvocationStrategy<Object>() {
      @Override
      public boolean accepts(final Method method) {
        return Collection.class.isAssignableFrom(method.getReturnType());
      }

      @Override
      public Object invokeMethod(final JavaMethod method, final Object proxy, final Object[] args, final List<Object> implementations)
        throws IllegalAccessException, InvocationTargetException {

        final Type type = DomReflectionUtil.extractCollectionElementType(method.getGenericReturnType());
        assert type != null : "No generic return type in method " + method;
        return getMergedImplementations(method.getMethod(), args, ReflectionUtil.getRawType(type), implementations, isIntersectionMethod(method));
      }
    });


    addInvocationStrategy(Object.class, new InvocationStrategy<Object>() {
      @Override
      public boolean accepts(final Method method) {
        return Object.class.equals(method.getDeclaringClass());
      }

      @Override
      public Object invokeMethod(final JavaMethod method, final Object proxy, final Object[] args, final List<Object> implementations) {
        @NonNls String methodName = method.getName();
        if ("toString".equals(methodName)) {
          return "Merger: " + implementations;
        }
        if ("hashCode".equals(methodName)) {
          int result = 1;

          for (Object element : implementations)
            result = 31 * result + element.hashCode();

          return result;
        }
        if ("equals".equals(methodName)) {
          final Object arg = args[0];
          return arg instanceof MergedObject && implementations.equals(((MergedObject)arg).getImplementations());

        }
        return null;
      }
    });

    addInvocationStrategy(Object.class, new InvocationStrategy<Object>() {
      @Override
      public boolean accepts(final Method method) {
        return "isValid".equals(method.getName());
      }

      @Override
      public Object invokeMethod(final JavaMethod method, final Object proxy, final Object[] args, final List<Object> implementations)
        throws IllegalAccessException, InvocationTargetException {
        for (final Object implementation : implementations) {
          if (!((Boolean)method.invoke(implementation, args))) {
            return Boolean.FALSE;
          }
        }
        return Boolean.TRUE;
      }
    });

    addInvocationStrategy(Object.class, new InvocationStrategy<Object>() {
      @Override
      public boolean accepts(final Method method) {
        return void.class.equals(method.getReturnType());
      }

      @Override
      public Object invokeMethod(final JavaMethod method, final Object proxy, final Object[] args, final List<Object> implementations)
        throws IllegalAccessException, InvocationTargetException {
        for (final Object t : implementations) {
          method.invoke(t, args);
        }
        return null;
      }
    });

    addInvocationStrategy(Object.class, new InvocationStrategy<Object>() {
      @Override
      public boolean accepts(final Method method) {
        return MERGED_OBJECT_CLASS.equals(method.getDeclaringClass());
      }

      @Override
      public Object invokeMethod(final JavaMethod method, final Object proxy, final Object[] args, final List<Object> implementations)
        throws IllegalAccessException, InvocationTargetException {
        assert "getImplementations".equals(method.getName());
        return implementations;
      }
    });

    addInvocationStrategy(DomElement.class, new InvocationStrategy<DomElement>() {
      @Override
      public boolean accepts(final Method method) {
        return DomInvocationHandler.ACCEPT_METHOD.equals(method);
      }

      @Override
      public Object invokeMethod(final JavaMethod method, final DomElement proxy, final Object[] args, final List<DomElement> implementations)
        throws IllegalAccessException, InvocationTargetException {
        final DomElementVisitor visitor = (DomElementVisitor)args[0];
        ((DomManagerImpl)implementations.get(0).getManager()).getApplicationComponent().getVisitorDescription(visitor.getClass()).acceptElement(visitor, proxy);
        return null;
      }
    });

    addInvocationStrategy(DomElement.class, new InvocationStrategy<DomElement>() {
      @Override
      public boolean accepts(final Method method) {
        return DomInvocationHandler.ACCEPT_CHILDREN_METHOD.equals(method);
      }

      @Override
      public Object invokeMethod(final JavaMethod method, final DomElement proxy, final Object[] args, final List<DomElement> implementations)
        throws IllegalAccessException, InvocationTargetException {
        final DomElementVisitor visitor = (DomElementVisitor)args[0];
        for (final AbstractDomChildrenDescription description : implementations.get(0).getGenericInfo().getChildrenDescriptions()) {
          for (final DomElement value : description.getValues(proxy)) {
            value.accept(visitor);
          }
        }
        return null;
      }
    });

  }

  private boolean isIntersectionMethod(final JavaMethod javaMethod) {
    return javaMethod.getMethod().getAnnotation(Intersect.class) != null;
  }

  @Override
  public final <T> void addInvocationStrategy(Class<T> aClass, InvocationStrategy<T> strategy) {
    myInvocationStrategies.add(Pair.create(strategy, aClass));
  }

  @Override
  public final <T> void addMergingStrategy(Class<T> aClass, MergingStrategy<T> strategy) {
    myMergingStrategies.add(strategy);
    myMergingStrategyClasses.add(aClass);
  }

  @Override
  public <T> T mergeModels(final Class<T> aClass, final T... implementations) {
    if (implementations.length == 1) return implementations[0];
    final MergingInvocationHandler<T> handler = new MergingInvocationHandler<>(aClass, Arrays.asList(implementations));
    return _mergeModels(aClass, handler, implementations);
  }

  @Override
  public <T> T mergeModels(final Class<T> aClass, final Collection<? extends T> implementations) {
    return (T)mergeModels((Class)aClass, implementations.toArray());
  }


  private <T> T _mergeModels(final Class<? super T> aClass, final MergingInvocationHandler<T> handler, final T... implementations) {
    final Set<Class> commonClasses = getCommonClasses(new THashSet<>(), (Object[])implementations);
    commonClasses.add(MERGED_OBJECT_CLASS);
    commonClasses.add(aClass);
    final T t = AdvancedProxy.createProxy(handler, null, commonClasses.toArray(new Class[commonClasses.size()]));
    return t;
  }

  private static <T extends Collection<Class>> T getCommonClasses(final T result, final Object... implementations) {
    if (implementations.length > 0) {
      DomUtil.getAllInterfaces(implementations[0].getClass(), result);
      for (int i = 1; i < implementations.length; i++) {
        final ArrayList<Class> list1 = new ArrayList<>();
        DomUtil.getAllInterfaces(implementations[i].getClass(), list1);
        result.retainAll(list1);
      }
    }
    return result;
  }


  private static final Map<Class<?>, Method> ourPrimaryKeyMethods = new HashMap<>();

  public class MergingInvocationHandler<T> implements InvocationHandler {
    private final Class<? super T> myClass;
    private List<T> myImplementations;

    public MergingInvocationHandler(final Class<T> aClass, final List<T> implementations) {
      this(aClass);
      for (final T implementation : implementations) {
        if (implementation instanceof StableElement) {
          throw new AssertionError("Stable values merging is prohibited: " + implementation);
        }
      }
      myImplementations = implementations;
    }

    public MergingInvocationHandler(final Class<T> aClass) {
      myClass = aClass;
    }

    @NotNull
    private InvocationStrategy findStrategy(final Object proxy, final Method method) {
      for (final Pair<InvocationStrategy, Class> pair : myAcceptsCache.get(method)) {
        if (Object.class.equals(pair.second) || pair.second.isInstance(proxy)) {
          return pair.first;
        }
      }
      throw new AssertionError("impossible");
    }

    @Override
    public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
      try {
        return findStrategy(proxy, method).invokeMethod(getJavaMethod(method), proxy, args, myImplementations);
      }
      catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }

    private JavaMethod getJavaMethod(final Method method) {
      if (ReflectionUtil.isAssignable(MERGED_OBJECT_CLASS, method.getDeclaringClass())) {
        return JavaMethod.getMethod(MERGED_OBJECT_CLASS, method);
      }
      if (ReflectionUtil.isAssignable(method.getDeclaringClass(), myClass)) {
        return JavaMethod.getMethod(myClass, method);
      }
      return JavaMethod.getMethod(method.getDeclaringClass(), method);
    }
  }

  @Nullable
  private static Object getPrimaryKey(Object implementation, final boolean singleValuedInvocation) {
    final Method method = getPrimaryKeyMethod(implementation.getClass());
    if (method != null) {
      final Object o = DomReflectionUtil.invokeMethod(method, implementation);
      return ReflectionUtil.isAssignable(GenericValue.class, method.getReturnType()) ? ((GenericValue)o).getValue() : o;
    }
    else {
      if (implementation instanceof GenericValue) {
        return singleValuedInvocation? Boolean.TRUE : ((GenericValue)implementation).getValue();
      }
      else {
        return null;
      }
    }
  }

  @Nullable
  private static Method getPrimaryKeyMethod(final Class<?> aClass) {
    Method method = ourPrimaryKeyMethods.get(aClass);
    if (method == null) {
      if (ourPrimaryKeyMethods.containsKey(aClass)) return null;

      for (final Method method1 : ReflectionUtil.getClassPublicMethods(aClass)) {
        if ((method = findPrimaryKeyAnnotatedMethod(method1, aClass)) != null) {
          break;
        }
      }
      ourPrimaryKeyMethods.put(aClass, method);
    }
    return method;
  }

  @Nullable
  private static Method findPrimaryKeyAnnotatedMethod(final Method method, final Class aClass) {
    return method.getReturnType() != void.class && method.getParameterTypes().length == 0 ? new JavaMethodSignature(method)
      .findAnnotatedMethod(PrimaryKey.class, aClass) : null;
  }

  private List<Object> getMergedImplementations(final Method method,
                                                final Object[] args,
                                                final Class returnType,
                                                final List<Object> implementations,
                                                final boolean intersect) throws IllegalAccessException, InvocationTargetException {

    final List<Object> results = new ArrayList<>();

    if (returnType.isInterface()) {
      final List<Object> orderedPrimaryKeys = new SmartList<>();
      final Map<Object, List<Set<Object>>> map = FactoryMap.create(key -> {
        orderedPrimaryKeys.add(key);
        return new SmartList<>();
      });
      final Map<Object, int[]> counts = FactoryMap.create(key -> new int[implementations.size()]);
      for (int i = 0; i < implementations.size(); i++) {
        Object t = implementations.get(i);
        final Object o = method.invoke(t, args);
        if (o instanceof Collection) {
          for (final Object o1 : (Collection)o) {
            addToMaps(o1, counts, map, i, results, false, intersect);
          }
        }
        else if (o != null) {
          addToMaps(o, counts, map, i, results, true, intersect);
        }

      }

      for (final Object primaryKey : orderedPrimaryKeys) {
        for (final Set<Object> objects : map.get(primaryKey)) {
          results.add(mergeImplementations(returnType, new ArrayList<>(objects)));
        }
      }
    }
    else {
      HashSet<Object> map = new HashSet<>();
      for (final Object t : implementations) {
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

  protected final Object mergeImplementations(final Class returnType, final List<Object> implementations) {
    for (int i = myMergingStrategies.size() - 1; i >= 0; i--) {
      if (ReflectionUtil.isAssignable(myMergingStrategyClasses.get(i), returnType)) {
        final Object o = myMergingStrategies.get(i).mergeChildren(returnType, implementations);
        if (o != null) {
          return o;
        }
      }
    }
    if (implementations.size() == 1) {
      return implementations.get(0);
    }
    return mergeModels(returnType, implementations);
  }

  private boolean addToMaps(final Object o,
                            final Map<Object, int[]> counts,
                            final Map<Object, List<Set<Object>>> map,
                            final int index,
                            final List<Object> results,
                            final boolean singleValuedInvocation,
                            final boolean intersect) {
    final Object primaryKey = getPrimaryKey(o, singleValuedInvocation);
    if (primaryKey != null || singleValuedInvocation) {
      final List<Set<Object>> list = map.get(primaryKey);
      final int[] indices = counts.get(primaryKey);
      int objIndex = intersect? indices[index] : indices[index]++;
      if (list.size() <= objIndex) {
        list.add(new LinkedHashSet<>());
      }
      list.get(objIndex).add(o);
      return false;
    }

    results.add(o);
    return true;
  }


}
