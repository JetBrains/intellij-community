/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import net.sf.cglib.proxy.InvocationHandler;
import net.sf.cglib.proxy.Proxy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class XmlAnnotatedElementManagerImpl extends XmlAnnotatedElementManager implements ApplicationComponent {
  private static final Key<NameStrategy> NAME_STRATEGY_KEY = new Key<NameStrategy>("NameStrategy");

  public <T extends XmlAnnotatedElement> T getXmlAnnotatedElement(final Class<T> aClass, final XmlTag tag) {
    return (T)Proxy.newProxyInstance(null, new Class[]{aClass}, new MyInvocationHandler(tag));
  }

  public void setNameStrategy(final XmlFile file, final NameStrategy strategy) {
    file.putUserData(NAME_STRATEGY_KEY, strategy);
  }

  public NameStrategy getNameStrategy(final XmlFile file) {
    final NameStrategy strategy = file.getUserData(NAME_STRATEGY_KEY);
    return strategy == null ? NameStrategy.HYPHEN_STRATEGY : strategy;
  }

  public <T extends XmlAnnotatedElement> XmlFileAnnotatedElement<T> getFileElement(final XmlFile file, final Class<T> aClass) {
    return new XmlFileAnnotatedElement<T>(file, this, aClass);
  }

  @NonNls
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private class MyInvocationHandler implements InvocationHandler {
    private final XmlTag myTag;
    private final XmlFile myFile;

    public MyInvocationHandler(final XmlTag tag) {
      myTag = tag;
      myFile = (XmlFile)tag.getContainingFile();
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      final AttributeValue attributeValue = method.getAnnotation(AttributeValue.class);
      if (attributeValue != null) {
        return processAttributeValue(attributeValue, method);
      }
      final TagValue tagValue = method.getAnnotation(TagValue.class);
      if (tagValue != null || isGetValueMethod(method)) {
        return myTag.getValue().getText();
      }
      final SubTagValue subTagValue = method.getAnnotation(SubTagValue.class);
      if (subTagValue != null) {
        return processSubTagValue(subTagValue, method);
      }

      final Class<?> returnType = method.getReturnType();
      final SubTag subTagAnnotation = method.getAnnotation(SubTag.class);
      if (subTagAnnotation != null && XmlAnnotatedElement.class.isAssignableFrom(returnType)) {
        return processSubTag(subTagAnnotation, method);
      }

      final SubTagList subTagList = method.getAnnotation(SubTagList.class);
      if (subTagList != null) {
        final Class<? extends XmlAnnotatedElement> aClass = extractElementType(method.getGenericReturnType());
        if (aClass != null) {
          return processSubTagList(subTagList, method, aClass);
        }
      }

      throw new UnsupportedOperationException();
    }

    private Object processSubTagList(final SubTagList subTagList, final Method method, final Class<? extends XmlAnnotatedElement> aClass) {
      final XmlTag[] subTags = myTag.findSubTags(guessSingularName(subTagList.value(), method));
      final ArrayList<XmlAnnotatedElement> list = new ArrayList<XmlAnnotatedElement>(subTags.length);
      for (XmlTag xmlTag : subTags) {
        list.add(getXmlAnnotatedElement(aClass, xmlTag));
      }
      return Collections.unmodifiableList(list);
    }

    private Object processSubTag(final SubTag subTagAnnotation, final Method method) {
      final XmlTag subTag = myTag.findFirstSubTag(guessName(subTagAnnotation.value(), method));
      return subTag == null ? null : getXmlAnnotatedElement((Class<XmlAnnotatedElement>)method.getReturnType(), subTag);
    }

    private boolean isGetValueMethod(final Method method) {
      return "getValue".equals(method.getName()) && String.class.equals(method.getReturnType()) && method.getParameterTypes().length == 0;
    }

    private Object processSubTagValue(final SubTagValue subTagValue, final Method method) {
      final XmlTag subTag = myTag.findFirstSubTag(guessName(subTagValue.value(), method));
      return subTag == null ? null : subTag.getValue().getText();
    }

    private Object processAttributeValue(final AttributeValue attributeValue, Method method) {
      return myTag.getAttributeValue(guessName(attributeValue.value(), method));
    }

    private String guessName(final String value, final Method method) {
      if (StringUtil.isEmpty(value)) {
        return getNameStrategy(myFile).convertName(getPropertyName(method));
      }
      return value;
    }

    private String guessSingularName(final String value, final Method method) {
      if (StringUtil.isEmpty(value)) {
        return getNameStrategy(myFile).convertName(StringUtil.unpluralize(getPropertyName(method)));
      }
      return value;
    }
  }

  @Nullable
  private static Class<? extends XmlAnnotatedElement> extractElementType(Type returnType) {
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
          }
          else if (argument instanceof Class) {
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

  private static String getPropertyName(Method method) {
    return PropertyUtil.getPropertyName(method.getName());
  }


}
