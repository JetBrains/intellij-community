/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiLock;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.*;

/**
 * @author peter
 */
class DomInvocationHandler<T extends DomElement> implements InvocationHandler, DomElement {
  private final Class<T> myClass;
  private final DomElement myParent;
  private final String myTagName;
  private XmlTag myTag;

  private XmlFile myFile;
  private DomElement myProxy;
  private boolean myInitialized = false;
  private boolean myInitializing = false;
  private final Map<Method,DomElement> myChildren = new HashMap<Method, DomElement>();

  public DomInvocationHandler(final Class<T> aClass, final XmlTag tag, final DomElement parent, final String tagName) {
    myClass = aClass;
    myTag = tag;
    myParent = parent;
    myTagName = tagName;
  }

  @NotNull
  public DomFileElement getRoot() {
    return myParent.getRoot();
  }

  @NotNull
  public DomElement getParent() {
    return myParent;
  }

  private <T> T convertFromString(Method method, String s) throws IllegalAccessException, InstantiationException {
    return ((Converter<T>)getConverter(method)).fromString(s, new ConvertContext(getFile()));
  }

  public final DomElement getProxy() {
    return myProxy;
  }

  public final void setProxy(final DomElement proxy) {
    myProxy = proxy;
  }

  @NotNull
  private static Converter getConverter(Method method) throws IllegalAccessException, InstantiationException {
    final Convert convert = method.getAnnotation(Convert.class);
    if (convert != null) {
      return convert.value().newInstance();
    }
    final Class<?> returnType = method.getReturnType();
    if (returnType.equals(int.class) || returnType.equals(Integer.class)) {
      return Converter.INTEGER_CONVERTER;
    }
    if (returnType.equals(boolean.class) || returnType.equals(Boolean.class)) {
      return Converter.BOOLEAN_CONVERTER;
    }
    return Converter.EMPTY_CONVERTER;
  }

  private XmlFile getFile() {
    if (myFile == null) {
      myFile = getRoot().getFile();
    }
    return myFile;
  }

  public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (isCoreMethod(method)) {
      if ("equals".equals(method.getName())) {
        return getProxy() == args[0];
      }
      return method.invoke(this, args);
    }

    final AttributeValue attributeValue = method.getAnnotation(AttributeValue.class);
    final XmlTag tag = getXmlTag();
    if (attributeValue != null) {
      return tag != null ? convertFromString(method, tag.getAttributeValue(guessName(attributeValue.value(), method))) : null;
    }
    final TagValue tagValue = method.getAnnotation(TagValue.class);
    if (tagValue != null || isGetValueMethod(method)) {
      return tag != null ? convertFromString(method, tag.getValue().getText()) : null;
    }

    checkInitialized();

    if (myChildren.containsKey(method)) {
      return myChildren.get(method);
    }
    final Class<?> returnType = method.getReturnType();
    if (tag != null && DomElement.class.isAssignableFrom(returnType)) {
      final String qname = getSubTagName(method);
      if (qname != null) {
        XmlTag subTag = tag.findFirstSubTag(qname);
        if (subTag != null) {
          final DomElement element = DomElementManagerImpl.getCachedElement(subTag);
          if (element != null) {
            myChildren.put(method, element);
            return element;
          }
        }
      }
    }

    if (extractElementType(method.getGenericReturnType()) != null) {
      if (tag == null) return Collections.emptyList();

      final String qname = getSubTagNameForCollection(method);
      if (qname != null) {
        final XmlTag[] subTags = tag.findSubTags(qname);
        DomElement[] elements = new DomElement[subTags.length];
        for (int i = 0; i < subTags.length; i++) {
          final DomElement element = DomElementManagerImpl.getCachedElement(subTags[i]);
          assert element != null : "Null annotated element for " + tag.getText() + "; " + qname + "; " + i;
          elements[i] = element;
        }
        return Arrays.asList(elements);
      }
    }


    throw new UnsupportedOperationException("Cannot call " + method.toString());
  }

  private boolean isCoreMethod(final Method method) {
    final Class<?> declaringClass = method.getDeclaringClass();
    return Object.class.equals(declaringClass) || DomElement.class.equals(declaringClass);
  }

  public String toString() {
    final XmlTag tag = getXmlTag();
    return StringUtil.getShortName(myClass) + " on tag " + (tag == null ? "null" : tag.getText()) + " @" + hashCode();
  }

  @Nullable
  private static Class<? extends DomElement> extractElementType(Type returnType) {
    if (returnType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType)returnType;
      final Type rawType = parameterizedType.getRawType();
      if (rawType instanceof Class) {
        final Class<?> rawClass = (Class<?>)rawType;
        if (List.class.isAssignableFrom(rawClass) || Collection.class.equals(rawClass)) {
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
                  if (DomElement.class.isAssignableFrom(aClass1)) {
                    return (Class<? extends DomElement>)aClass1;
                  }
                }
              }
            }
            else if (argument instanceof Class) {
              Class aClass1 = (Class)argument;
              if (DomElement.class.isAssignableFrom(aClass1)) {
                return (Class<? extends DomElement>)aClass1;
              }
            }
          }
        }
      }
    }
    return null;
  }


  private void checkInitialized() {
    synchronized (PsiLock.LOCK) {
      if (myInitialized || myInitializing) return;
      myInitializing = true;
      try {
        final HashSet<XmlTag> tags = new HashSet<XmlTag>();
        for (Method method : myClass.getMethods()) {
          if (!isCoreMethod(method)) {
            createElement(method, tags);
          }
        }
      }
      finally {
        myInitializing = false;
        myInitialized = true;
      }
    }
  }

  private void createElement(final Method method, final Set<XmlTag> tags) {
    final Class<?> returnType = method.getReturnType();
    final XmlTag tag = getXmlTag();

    if (DomElement.class.isAssignableFrom(returnType)) {
      final String qname = getSubTagName(method);
      if (qname != null) {
        XmlTag subTag = tag == null ? null : tag.findFirstSubTag(qname);
        myChildren.put(method, DomElementManagerImpl.createXmlAnnotatedElement((Class<DomElement>)returnType, subTag, getProxy(), qname));
        tags.add(subTag);
        return;
      }
    }
    if (tag != null) {
      final Class<? extends DomElement> aClass = extractElementType(method.getGenericReturnType());
      if (aClass != null) {
        final String qname = getSubTagNameForCollection(method);
        if (qname != null) {
          for (int i = 0; i < tag.findSubTags(qname).length; i++) {
            XmlTag subTag = tag.findSubTags(qname)[i];
            if (!tags.contains(subTag)) {
              DomElementManagerImpl.createXmlAnnotatedElement(aClass, subTag, getProxy(), qname);
              tags.add(subTag);
            }
          }
        }
      }
    }
  }

  @Nullable
  private String getSubTagName(final Method method) {
    final SubTag subTagAnnotation = method.getAnnotation(SubTag.class);
    if (subTagAnnotation == null || StringUtil.isEmpty(subTagAnnotation.value())) {
      return getNameFromMethod(method);
    }
    return subTagAnnotation.value();
  }

  @Nullable
  private String getSubTagNameForCollection(final Method method) {
    final SubTagList subTagList = method.getAnnotation(SubTagList.class);
    if (subTagList == null || StringUtil.isEmpty(subTagList.value())) {
      final String propertyName = getPropertyName(method);
      return propertyName != null ? getNameStrategy().convertName(StringUtil.unpluralize(propertyName)) : null;
    }
    return subTagList.value();
  }

  private static String getPropertyName(Method method) {
    return PropertyUtil.getPropertyName(method.getName());
  }

  @NotNull
  private NameStrategy getNameStrategy() {
    return DomElementManagerImpl._getNameStrategy(getFile());
  }

  private boolean isGetValueMethod(final Method method) {
    return "getValue".equals(method.getName()) && method.getParameterTypes().length == 0;
  }

  @Nullable
  private String guessName(final String value, final Method method) {
    if (StringUtil.isEmpty(value)) {
      return getNameFromMethod(method);
    }
    return value;
  }

  @Nullable
  private String getNameFromMethod(final Method method) {
    final String propertyName = getPropertyName(method);
    return propertyName == null ? null : getNameStrategy().convertName(propertyName);
  }

  @Nullable
  public XmlTag getXmlTag() {
    if (myTag == null && myTagName != null) {
      final DomFileElement root = myParent.getRoot();
      if (myParent == root) {
        final XmlTag tag = root.getRootTag();
        if (myTagName.equals(tag.getName())) {
          myTag = tag;
        }
      }
      else {
        final XmlTag tag = myParent.getXmlTag();
        if (tag != null) {
          myTag = tag.findFirstSubTag(myTagName);
        }
      }
      synchronized (PsiLock.LOCK) {
        DomElementManagerImpl.setCachedElement(myTag, getProxy());
      }
    }
    return myTag;
  }
}
