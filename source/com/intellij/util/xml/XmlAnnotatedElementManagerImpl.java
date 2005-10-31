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
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.PsiLock;
import net.sf.cglib.proxy.InvocationHandler;
import net.sf.cglib.proxy.Proxy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.*;

/**
 * @author peter
 */
public class XmlAnnotatedElementManagerImpl extends XmlAnnotatedElementManager implements ApplicationComponent {
  private static final Key<NameStrategy> NAME_STRATEGY_KEY = Key.create("NameStrategy");
  private static final Key<XmlAnnotatedElement> CACHED_ELEMENT = Key.create("CachedXmlAnnotatedElement");

  @NotNull
  protected <T extends XmlAnnotatedElement> T createXmlAnnotatedElement(final Class<T> aClass, final XmlTag tag) {
    synchronized (PsiLock.LOCK) {
      final T element = (T)Proxy.newProxyInstance(null, new Class[]{aClass}, new MyInvocationHandler<T>(tag, aClass));
      setCachedElement(tag, element);
      return element;
    }
  }

  public void setNameStrategy(final XmlFile file, final NameStrategy strategy) {
    file.putUserData(NAME_STRATEGY_KEY, strategy);
  }

  @NotNull
  public NameStrategy getNameStrategy(final XmlFile file) {
    final NameStrategy strategy = file.getUserData(NAME_STRATEGY_KEY);
    return strategy == null ? NameStrategy.HYPHEN_STRATEGY : strategy;
  }

  @NotNull
  public <T extends XmlAnnotatedElement> XmlFileAnnotatedElement<T> getFileElement(final XmlFile file, final Class<T> aClass) {
    synchronized (PsiLock.LOCK) {
      XmlFileAnnotatedElement<T> element = (XmlFileAnnotatedElement<T>)getCachedElement(file);
      if (element == null) {
        element = new XmlFileAnnotatedElement<T>(file, this, aClass);
        setCachedElement(file, element);
      }
      return element;
    }
  }

  @NotNull
  public <T extends XmlAnnotatedElement> XmlFileAnnotatedElement<T> getFileElement(final XmlFile file, final Class<T> aClass, String rootTagName) {
    synchronized (PsiLock.LOCK) {
      XmlFileAnnotatedElement<T> element = (XmlFileAnnotatedElement<T>)getCachedElement(file);
      if (element == null) {
        element = new XmlFileAnnotatedElement<T>(file, this, aClass, rootTagName);
        setCachedElement(file, element);
      }
      return element;
    }
  }

  private void setCachedElement(final XmlElement xmlElement, final XmlAnnotatedElement element) {
    xmlElement.putUserData(CACHED_ELEMENT, element);
  }

  @Nullable
  public XmlAnnotatedElement getCachedElement(final XmlElement element) {
    return element.getUserData(CACHED_ELEMENT);
  }

  @NonNls
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private Converter getConverter(Method method) throws IllegalAccessException, InstantiationException {
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



  private class MyInvocationHandler<T extends XmlAnnotatedElement> implements InvocationHandler {
    private final XmlTag myTag;
    private final XmlFile myFile;
    private final Class<T> myClass;
    private boolean myInitialized = false;
    private boolean myInitializing = false;

    public MyInvocationHandler(final XmlTag tag, final Class<T> aClass) {
      myTag = tag;
      myFile = (XmlFile)tag.getContainingFile();
      myClass = aClass;
    }

    private <T> T convertFromString(Method method, String s) throws IllegalAccessException, InstantiationException {
      return ((Converter<T>)getConverter(method)).fromString(s, new ConvertContext(myFile));
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      final AttributeValue attributeValue = method.getAnnotation(AttributeValue.class);
      if (attributeValue != null) {
        return convertFromString(method, myTag.getAttributeValue(guessName(attributeValue.value(), method)));
      }
      final TagValue tagValue = method.getAnnotation(TagValue.class);
      if (tagValue != null || isGetValueMethod(method)) {
        return convertFromString(method, myTag.getValue().getText());
      }
      final SubTagValue subTagValue = method.getAnnotation(SubTagValue.class);
      if (subTagValue != null) {
        final String qname = guessName(subTagValue.value(), method);
        if (qname != null) {
          final XmlTag subTag = myTag.findFirstSubTag(qname);
          if (subTag != null) {
            return convertFromString(method, subTag.getValue().getText());
          }
        }
        return null;
      }

      if (Object.class.equals(method.getDeclaringClass())) {
        final String name = method.getName();
        if ("toString".equals(name)) {
          return StringUtil.getShortName(myClass) + " on tag " + myTag.getText() + " @" + System.identityHashCode(proxy);
        }
        if ("equals".equals(name)) {
          return proxy == args[0];
        }
        if ("hashCode".equals(name)) {
          return System.identityHashCode(proxy);
        }
      }

      checkInitialized();

      if (XmlAnnotatedElement.class.isAssignableFrom(method.getReturnType())) {
        final String qname = getSubTagName(method);
        if (qname != null) {
          final XmlTag subTag = myTag.findFirstSubTag(qname);
          if (subTag != null) {
            final XmlAnnotatedElement element = getCachedElement(subTag);
            assert element != null : "Null annotated element for " + myTag.getText() + "; " + qname;
            return element;
          }
        }
      }

      if (extractElementType(method.getGenericReturnType()) != null) {
        final String qname = getSubTagNameForCollection(method);
        if (qname != null) {
          final XmlTag[] subTags = myTag.findSubTags(qname);
          XmlAnnotatedElement[] elements = new XmlAnnotatedElement[subTags.length];
          for (int i = 0; i < subTags.length; i++) {
            final XmlAnnotatedElement element = getCachedElement(subTags[i]);
            assert element != null : "Null annotated element for " + myTag.getText() + "; " + qname + "; " + i;
            elements[i] = element;
          }
          return Arrays.asList(elements);
        }
      }

      throw new UnsupportedOperationException("Cannot call " + method.toString());
    }

    private void checkInitialized() {
      synchronized (PsiLock.LOCK) {
        if (myInitialized || myInitializing) return;
        myInitializing = true;
        try {
          for (Method method : myClass.getMethods()) {
            createElement(method);
          }
        }
        finally {
          myInitializing = false;
          myInitialized = true;
        }
      }
    }

    private void createElement(final Method method) {
      final Class<?> returnType = method.getReturnType();
      if (XmlAnnotatedElement.class.isAssignableFrom(returnType)) {
        final String qname = getSubTagName(method);
        if (qname != null) {
          final XmlTag subTag = myTag.findFirstSubTag(qname);
          if (subTag != null) {
            createXmlAnnotatedElement((Class<XmlAnnotatedElement>)returnType, subTag);
            return;
          }
        }
      }
      final Class<? extends XmlAnnotatedElement> aClass = extractElementType(method.getGenericReturnType());
      if (aClass != null) {
        final String qname = getSubTagNameForCollection(method);
        if (qname != null) {
          for (XmlTag xmlTag : myTag.findSubTags(qname)) {
            createXmlAnnotatedElement(aClass, xmlTag);
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
        return propertyName != null ? getNameStrategy(myFile).convertName(StringUtil.unpluralize(propertyName)) : null;
      }
      return subTagList.value();
    }

    private boolean isGetValueMethod(final Method method) {
      return "getValue".equals(method.getName()) && String.class.equals(method.getReturnType()) && method.getParameterTypes().length == 0;
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
      return propertyName == null ? null : getNameStrategy(myFile).convertName(propertyName);
    }

  }

  @Nullable
  private static Class<? extends XmlAnnotatedElement> extractElementType(Type returnType) {
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
    }
    return null;
  }

  private static String getPropertyName(Method method) {
    return PropertyUtil.getPropertyName(method.getName());
  }


}
