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
package com.intellij.util.xml.impl;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Key;
import com.intellij.pom.Navigatable;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

class InvocationCache {
  private static final Map<JavaMethodSignature, Invocation> ourCoreInvocations = new HashMap<>();
  private final Map<Method, Invocation> myInvocations =
    ConcurrentFactoryMap.createMap(key -> ourCoreInvocations.get(new JavaMethodSignature(key)));
  private final Map<Method, JavaMethod> myJavaMethods;
  private final Map<JavaMethod, Boolean> myGetters = ConcurrentFactoryMap.createMap(key -> DomImplUtil.isTagValueGetter(key));
  private final Map<JavaMethod, Boolean> mySetters = ConcurrentFactoryMap.createMap(key -> DomImplUtil.isTagValueSetter(key));
  private final Map<JavaMethod, Map<Class<? extends Annotation>, Object>> myMethodAnnotations =
    ConcurrentFactoryMap.createMap(method -> ConcurrentFactoryMap.createMap(annoClass->
        method.getAnnotation(annoClass)
    )
    );
  private final Map<Class, Object> myClassAnnotations;
  private final Class myType;
  final StaticGenericInfo genericInfo;


  static {
    addCoreInvocations(DomElement.class);
    addCoreInvocations(Navigatable.class);
    addCoreInvocations(AnnotatedElement.class);
    addCoreInvocations(Object.class);
    ourCoreInvocations.put(new JavaMethodSignature("getUserData", Key.class), (handler, args) -> handler.getUserData((Key<?>)args[0]));
    ourCoreInvocations.put(new JavaMethodSignature("putUserData", Key.class, Object.class), (handler, args) -> {
      //noinspection unchecked
      handler.putUserData((Key)args[0], args[1]);
      return null;
    });
    ourCoreInvocations.put(new JavaMethodSignature("getXmlElement"), (handler, args) -> handler.getXmlElement());
    ourCoreInvocations.put(new JavaMethodSignature("getXmlTag"), (handler, args) -> handler.getXmlTag());
    ourCoreInvocations.put(new JavaMethodSignature("getParent"), (handler, args) -> handler.getParent());
    ourCoreInvocations.put(new JavaMethodSignature("accept", DomElementVisitor.class), (handler, args) -> {
      handler.accept((DomElementVisitor)args[0]);
      return null;
    });
    ourCoreInvocations.put(new JavaMethodSignature("acceptChildren", DomElementVisitor.class), (handler, args) -> {
      handler.acceptChildren((DomElementVisitor)args[0]);
      return null;
    });
    ourCoreInvocations.put(new JavaMethodSignature("getAnnotation", Class.class), (handler, args) -> {
      //noinspection unchecked
      return handler.getAnnotation((Class<Annotation>)args[0]);
    });
    ourCoreInvocations.put(new JavaMethodSignature("getRawText"), (handler, args) -> handler.getValue());
    ourCoreInvocations.put(new JavaMethodSignature("getXmlAttribute"), (handler, args) -> handler.getXmlElement());
    ourCoreInvocations.put(new JavaMethodSignature("getXmlAttributeValue"), (handler, args) -> {
      final XmlAttribute attribute = (XmlAttribute)handler.getXmlElement();
      return attribute != null ? attribute.getValueElement() : null;
    });
    ourCoreInvocations.put(new JavaMethodSignature("getConverter"), (handler, args) -> {
      try {
        return handler.getScalarConverter();
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        final Throwable cause = e.getCause();
        if (cause instanceof ProcessCanceledException) {
          throw cause;
        }
        throw new RuntimeException(e);
      }
    });
  }

  private static void addCoreInvocations(final Class<?> aClass) {
    for (final Method method : ReflectionUtil.getClassDeclaredMethods(aClass)) {
      if ("equals".equals(method.getName())) {
        ourCoreInvocations.put(new JavaMethodSignature(method), (handler, args) -> {
          final DomElement proxy = handler.getProxy();
          final Object arg = args[0];
          if (proxy == arg) return true;
          if (arg == null) return false;

          if (arg instanceof DomElement) {
            final DomInvocationHandler handler1 = DomManagerImpl.getDomInvocationHandler(proxy);
            return handler1 != null && handler1.equals(DomManagerImpl.getDomInvocationHandler((DomElement)arg));
          }

          return false;
        });
      }
      else if ("hashCode".equals(method.getName())) {
        ourCoreInvocations.put(new JavaMethodSignature(method), (handler, args) -> handler.hashCode());
      }
      else {
        ourCoreInvocations.put(new JavaMethodSignature(method), (handler, args) -> method.invoke(handler, args));
      }
    }
  }

  InvocationCache(Class type) {
    myType = type;
    myJavaMethods = ConcurrentFactoryMap.createMap(key -> JavaMethod.getMethod(myType, key));
    myClassAnnotations = ConcurrentFactoryMap.createMap(annoClass -> myType.getAnnotation(annoClass));
    genericInfo = new StaticGenericInfo(type);
  }

  @Nullable
  Invocation getInvocation(Method method) {
    Invocation invocation = myInvocations.get(method);
    if (invocation == null) {
      invocation = genericInfo.createInvocation(getInternedMethod(method));
      if (invocation != null) {
        myInvocations.put(method, invocation);
      }
    }
    return invocation;
  }

  JavaMethod getInternedMethod(Method method) {
    return myJavaMethods.get(method);
  }

  boolean isTagValueGetter(JavaMethod method) {
    return myGetters.get(method);
  }

  boolean isTagValueSetter(JavaMethod method) {
    return mySetters.get(method);
  }

  @Nullable
  <T extends Annotation> T getMethodAnnotation(JavaMethod method, Class<T> annoClass) {
    //noinspection unchecked
    return (T)myMethodAnnotations.get(method).get(annoClass);
  }

  @Nullable
  <T extends Annotation> T getClassAnnotation(Class<T> annoClass) {
    //noinspection unchecked
    return (T)myClassAnnotations.get(annoClass);
  }
}
