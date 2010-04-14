/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NonNls;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public final class JavaMethod implements AnnotatedElement{
  public static final JavaMethod[] EMPTY_ARRAY = new JavaMethod[0];

  private static final FactoryMap<JavaMethodSignature,FactoryMap<Class,JavaMethod>> ourMethods = new FactoryMap<JavaMethodSignature, FactoryMap<Class, JavaMethod>>() {
    protected FactoryMap<Class, JavaMethod> create(final JavaMethodSignature signature) {
      return new FactoryMap<Class, JavaMethod>() {
        protected JavaMethod create(final Class key) {
          return new JavaMethod(key, signature);
        }
      };
    }
  };

  private final JavaMethodSignature mySignature;
  private final Class myDeclaringClass;
  private final Method myMethod;
  private final FactoryMap<Class, Annotation> myAnnotationsMap = new ConcurrentFactoryMap<Class, Annotation>() {

    protected Annotation create(Class key) {
      return mySignature.findAnnotation(key, myDeclaringClass);
    }
  };

  private JavaMethod(final Class declaringClass, final JavaMethodSignature signature) {
    mySignature = signature;
    myMethod = signature.findMethod(declaringClass);
    assert myMethod != null : "No method " + signature + " in class " + declaringClass;
    myDeclaringClass = myMethod.getDeclaringClass();
  }

  public final Class getDeclaringClass() {
    return myDeclaringClass;
  }

  public final JavaMethodSignature getSignature() {
    return mySignature;
  }

  public final Method getMethod() {
    return myMethod;
  }

  public final Type[] getGenericParameterTypes() {
    return myMethod.getGenericParameterTypes();
  }

  public final Type getGenericReturnType() {
    return myMethod.getGenericReturnType();
  }

  public static JavaMethod getMethod(final Class declaringClass, final JavaMethodSignature signature) {
    synchronized (ourMethods) {
      return ourMethods.get(signature).get(declaringClass);
    }
  }

  public static JavaMethod getMethod(final Class declaringClass, final Method method) {
    return getMethod(declaringClass, JavaMethodSignature.getSignature(method));
  }

  public final Object invoke(final Object o, final Object... args) {
    return DomReflectionUtil.invokeMethod(myMethod, o, args);
  }

  public String toString() {
    return "JavaMethod: " + myMethod.toString();
  }

  @NonNls
  public final String getName() {
    return myMethod.getName();
  }

  public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return (T)myAnnotationsMap.get(annotationClass);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaMethod)) return false;

    final JavaMethod that = (JavaMethod)o;

    if (!myDeclaringClass.equals(that.myDeclaringClass)) return false;
    if (!mySignature.equals(that.mySignature)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = mySignature.hashCode();
    result = 31 * result + myDeclaringClass.hashCode();
    return result;
  }

  public final Class getReturnType() {
    return myMethod.getReturnType();
  }

  public Class<?>[] getParameterTypes() {
    return myMethod.getParameterTypes();
  }
}
