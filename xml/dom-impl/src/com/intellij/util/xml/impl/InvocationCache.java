/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.pom.Navigatable;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.ui.DomUIFactory;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class InvocationCache {
  private static final Map<JavaMethodSignature, Invocation> ourCoreInvocations = new HashMap<JavaMethodSignature, Invocation>();
  private final Map<JavaMethodSignature, Invocation> myInvocations = new ConcurrentHashMap<JavaMethodSignature, Invocation>();

  static {
    addCoreInvocations(DomElement.class);
    addCoreInvocations(Navigatable.class);
    addCoreInvocations(AnnotatedElement.class);
    addCoreInvocations(UserDataHolder.class);
    addCoreInvocations(Object.class);
    try {
      ourCoreInvocations.put(JavaMethodSignature.getSignature(DomElement.class.getMethod("getXmlElement")), new Invocation() {
        public Object invoke(DomInvocationHandler<?> handler, Object[] args) throws Throwable {
          return handler.getXmlElement();
        }
      });
      ourCoreInvocations.put(JavaMethodSignature.getSignature(DomElement.class.getMethod("getXmlTag")), new Invocation() {
        public Object invoke(DomInvocationHandler<?> handler, Object[] args) throws Throwable {
          return handler.getXmlTag();
        }
      });
      ourCoreInvocations.put(JavaMethodSignature.getSignature(DomElement.class.getMethod("getParent")), new Invocation() {
        public Object invoke(DomInvocationHandler<?> handler, Object[] args) throws Throwable {
          return handler.getParent();
        }
      });
      ourCoreInvocations.put(JavaMethodSignature.getSignature(DomElement.class.getMethod("accept", DomElementVisitor.class)), new Invocation() {
        public Object invoke(DomInvocationHandler<?> handler, Object[] args) throws Throwable {
          handler.accept((DomElementVisitor)args[0]);
          return null;
        }
      });
      ourCoreInvocations.put(JavaMethodSignature.getSignature(AnnotatedElement.class.getMethod("getAnnotation", Class.class)), new Invocation() {
        public Object invoke(DomInvocationHandler<?> handler, Object[] args) throws Throwable {
          return handler.getAnnotation((Class<Annotation>)args[0]);
        }
      });
      ourCoreInvocations.put(JavaMethodSignature.getSignature(GenericDomValue.class.getMethod("getRawText")), new Invocation() {
        public final Object invoke(final DomInvocationHandler<?> handler, final Object[] args) throws Throwable {
          return handler.getValue();
        }
      });
      ourCoreInvocations.put(JavaMethodSignature.getSignature(GenericAttributeValue.class.getMethod("getXmlAttribute")), new Invocation() {
        public final Object invoke(final DomInvocationHandler<?> handler, final Object[] args) throws Throwable {
          return handler.getXmlElement();
        }
      });
      ourCoreInvocations.put(JavaMethodSignature.getSignature(GenericAttributeValue.class.getMethod("getXmlAttributeValue")), new Invocation() {
        @Nullable
        public final Object invoke(final DomInvocationHandler<?> handler, final Object[] args) throws Throwable {
          final XmlAttribute attribute = (XmlAttribute)handler.getXmlElement();
          return attribute != null ? attribute.getValueElement() : null;
        }
      });
      final JavaMethod javaMethod =
              JavaMethod.getMethod(GenericValue.class, JavaMethodSignature.getSignature(DomUIFactory.GET_VALUE_METHOD));
      ourCoreInvocations.put(JavaMethodSignature.getSignature(GenericDomValue.class.getMethod("getConverter")), new Invocation() {
        public final Object invoke(final DomInvocationHandler<?> handler, final Object[] args) throws Throwable {
          try {
            return handler.getScalarConverter(javaMethod);
          }
          catch (Throwable e) {
            final Throwable cause = e.getCause();
            if (cause instanceof ProcessCanceledException) {
              throw(ProcessCanceledException)cause;
            }
            throw new RuntimeException(e);
          }
        }
      });
    }
    catch (NoSuchMethodException e) {
      throw new AssertionError();
    }
  }

  private static void addCoreInvocations(final Class<?> aClass) {
    for (final Method method : aClass.getDeclaredMethods()) {
      if ("equals".equals(method.getName())) {
        ourCoreInvocations.put(JavaMethodSignature.getSignature(method), new Invocation() {
          public Object invoke(DomInvocationHandler<?> handler, Object[] args) throws Throwable {
            final DomElement proxy = handler.getProxy();
            final Object arg = args[0];
            if (proxy == arg) return true;
            if (arg == null) return false;

            if (arg instanceof DomElement) {
              final DomInvocationHandler handler1 = DomManagerImpl.getDomInvocationHandler(proxy);
              return handler1 != null && handler1.equals(DomManagerImpl.getDomInvocationHandler((DomElement)arg));
            }

            return false;
          }

        });
      }
      else if ("hashCode".equals(method.getName())) {
        ourCoreInvocations.put(JavaMethodSignature.getSignature(method), new Invocation() {
          public Object invoke(DomInvocationHandler<?> handler, Object[] args) throws Throwable {
            return handler.hashCode();
          }
        });
      }
      else {
        ourCoreInvocations.put(JavaMethodSignature.getSignature(method), new Invocation() {
          public Object invoke(DomInvocationHandler<?> handler, Object[] args) throws Throwable {
            return method.invoke(handler, args);
          }
        });
      }
    }
  }


  public Invocation getInvocation(JavaMethodSignature method) {
    Invocation invocation = ourCoreInvocations.get(method);
    return invocation != null ? invocation : myInvocations.get(method);
  }

  public void putInvocation(JavaMethodSignature method, Invocation invocation) {
    myInvocations.put(method, invocation);
  }

}
