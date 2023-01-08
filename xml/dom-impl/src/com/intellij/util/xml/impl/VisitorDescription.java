// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ClassMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.DomReflectionUtil;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public final class VisitorDescription {
  private final Class<? extends DomElementVisitor> myVisitorClass;
  private final ClassMap<Method> myMethods = new ClassMap<>(new ConcurrentHashMap<>());
  @NonNls private static final String VISIT = "visit";

  public VisitorDescription(final Class<? extends DomElementVisitor> visitorClass) {
    myVisitorClass = visitorClass;
    for (final Method method : ReflectionUtil.getClassPublicMethods(visitorClass)) {
      if (method.getParameterCount() != 1) {
        continue;
      }
      final Class<?>[] parameterTypes = method.getParameterTypes();
      final Class<?> domClass = parameterTypes[0];
      if (!ReflectionUtil.isAssignable(DomElement.class, domClass)) {
        continue;
      }
      final String methodName = method.getName();
      if (/*VISIT.equals(methodName) ||*/ methodName.startsWith(VISIT) /*&& domClass.getSimpleName().equals(methodName.substring(VISIT.length()))*/) {
        method.setAccessible(true);
        myMethods.put(domClass, method);
      }
    }
  }

  public void acceptElement(DomElementVisitor visitor, DomElement element) {
    final Method method = myMethods.get(element.getClass());
    assert method != null : myVisitorClass + " can't accept element of type " + element.getClass();
    DomReflectionUtil.invokeMethod(method, visitor, element);
  }

}
