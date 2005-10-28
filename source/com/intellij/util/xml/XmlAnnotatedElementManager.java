/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import net.sf.cglib.proxy.InvocationHandler;
import net.sf.cglib.proxy.Proxy;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author peter
 */
public class XmlAnnotatedElementManager {
  public static <T extends XmlAnnotatedElement> T getXmlAnnotatedElement(final Class<T> aClass, final XmlFile file) {
    return getXmlAnnotatedElement(aClass, file.getDocument().getRootTag());
  }

  public static <T extends XmlAnnotatedElement> T getXmlAnnotatedElement(final Class<T> aClass, final XmlTag tag) {
    final Object o = Proxy.newProxyInstance(null, new Class[]{aClass}, new InvocationHandler() {
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final AttributeValue attributeValue = method.getAnnotation(AttributeValue.class);
        if (attributeValue != null) {
          return tag.getAttributeValue(attributeValue.value());
        }
        final TagValue tagValue = method.getAnnotation(TagValue.class);
        if (tagValue != null) {
          return tag.getValue().getText();
        }
        final SubTagValue subTagValue = method.getAnnotation(SubTagValue.class);
        if (subTagValue != null) {
          final XmlTag subTag = tag.findFirstSubTag(subTagValue.value());
          return subTag == null ? null : subTag.getValue().getText();
        }

        final Class<?> returnType = method.getReturnType();
        final SubTag subTagAnnotation = method.getAnnotation(SubTag.class);
        if (subTagAnnotation != null && XmlAnnotatedElement.class.isAssignableFrom(returnType)) {
          final XmlTag subTag = tag.findFirstSubTag(subTagAnnotation.value());
          return subTag == null ? null : getXmlAnnotatedElement((Class<XmlAnnotatedElement>)returnType, subTag);
        }

        final SubTagList subTagList = method.getAnnotation(SubTagList.class);
        if (subTagList != null) {
          final Class<? extends XmlAnnotatedElement> aClass = extractElementType(method.getGenericReturnType());
          if (aClass != null) {
            final XmlTag[] subTags = tag.findSubTags(subTagList.value());
            final ArrayList<XmlAnnotatedElement> list = new ArrayList<XmlAnnotatedElement>(subTags.length);
            for (XmlTag xmlTag : subTags) {
              list.add(getXmlAnnotatedElement(aClass, xmlTag));
            }
            return Collections.unmodifiableList(list);
          }
        }



        throw new UnsupportedOperationException();
      }

      @Nullable
      private Class<? extends XmlAnnotatedElement> extractElementType(Type returnType) {
        if (returnType instanceof ParameterizedType) {
          ParameterizedType parameterizedType = (ParameterizedType)returnType;
          if (List.class.equals(parameterizedType.getRawType())) {
            final Type[] arguments = parameterizedType.getActualTypeArguments();
            if (arguments.length == 1) {
              final Type argument = arguments[0];
              if (argument instanceof WildcardType) {
                WildcardType wildcardType = (WildcardType)argument;
                final Type[] upperBounds = wildcardType.getUpperBounds();
                if (upperBounds.length == 1) {
                  final Type upperBound = upperBounds[0];
                  if (upperBound instanceof Class) {
                    Class aClass1 = (Class)upperBound;
                    if (XmlAnnotatedElement.class.isAssignableFrom(aClass1)) {
                      return (Class<? extends XmlAnnotatedElement>)aClass1;
                    }
                  }
                }
              } else if (argument instanceof Class) {
                Class aClass1 = (Class)argument;
                if (XmlAnnotatedElement.class.isAssignableFrom(aClass1)) {
                  return (Class<? extends XmlAnnotatedElement>)aClass1;
                }
              }
            }
          }
        }
        return null;
      }
    });
    return (T)o;
  }


}
