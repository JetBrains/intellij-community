// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.MergedObject;
import com.intellij.util.xml.StableElement;
import net.sf.cglib.proxy.AdvancedProxy;
import net.sf.cglib.proxy.InvocationHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class StableInvocationHandler<T> implements InvocationHandler, StableElement {
  private T oldValue;
  private T cachedValue;
  private final Set<Class<?>> classes;
  private final Supplier<? extends T> provider;
  private final Predicate<? super T> validator;

  StableInvocationHandler(final T initial, final Supplier<? extends T> provider, Predicate<? super T> validator) {
    this.provider = provider;
    cachedValue = initial;
    oldValue = initial;
    this.validator = validator;
    Class<?> superClass = initial.getClass().getSuperclass();

    Set<Class<?>> classes = new HashSet<>();
    ContainerUtil.addAll(classes, initial.getClass().getInterfaces());
    ContainerUtil.addIfNotNull(classes, superClass);
    classes.remove(MergedObject.class);
    this.classes = classes;
  }


  @Override
  public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
    if (StableElement.class.equals(method.getDeclaringClass())) {
      try {
        return method.invoke(this, args);
      }
      catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }

    if (AdvancedProxy.FINALIZE_METHOD.equals(method)) return null;

    if (isNotValid(cachedValue)) {
      if (cachedValue != null) {
        oldValue = cachedValue;
      }
      cachedValue = provider.get();
      if (isNotValid(cachedValue)) {
        if (AdvancedProxy.EQUALS_METHOD.equals(method)) {

          final Object arg = args[0];
          if (!(arg instanceof StableElement)) return false;

          final StableInvocationHandler<?> handler = DomManagerImpl.getStableInvocationHandler(arg);
          if (handler == null || handler.getWrappedElement() != null) return false;

          return Comparing.equal(oldValue, handler.oldValue);
        }

        if (oldValue != null && Object.class.equals(method.getDeclaringClass())) {
          return method.invoke(oldValue, args);
        }

        if ("isValid".equals(method.getName())) {
          return Boolean.FALSE;
        }
        throw new AssertionError("Calling methods on invalid value");
      }
    }

    if (AdvancedProxy.EQUALS_METHOD.equals(method)) {
      final Object arg = args[0];
      if (arg instanceof StableElement) {
        return cachedValue.equals(((StableElement<?>)arg).getWrappedElement());
      }
      return cachedValue.equals(arg);

    }
    if (AdvancedProxy.HASHCODE_METHOD.equals(method)) {
      return cachedValue.hashCode();
    }

    try {
      return method.invoke(cachedValue, args);
    }
    catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  @Override
  public void revalidate() {
    final T t = provider.get();
    if (!isNotValid(t) && !t.equals(cachedValue)) {
      cachedValue = t;
    }
  }

  @Override
  public void invalidate() {
    if (!isNotValid(cachedValue)) {
      cachedValue = null;
    }
  }

  @Override
  public T getWrappedElement() {
    if (isNotValid(cachedValue)) {
      cachedValue = provider.get();
    }
    return cachedValue;
  }

  public T getOldValue() {
    return oldValue;
  }

  private boolean isNotValid(final T t) {
    if (t == null || !validator.test(t)) return true;
    for (final Class<?> aClass : classes) {
      if (!aClass.isInstance(t)) return true;
    }
    return false;
  }
}
