/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiLock;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.DomInvocationHandler");

  private final Class<T> myClass;
  private final DomElement myParent;
  @NotNull private final String myTagName;
  private XmlTag myTag;

  private XmlFile myFile;
  private DomElement myProxy;
  private boolean myInitialized = false;
  private boolean myInitializing = false;
  private final Map<Method, DomElement> myChildren = new HashMap<Method, DomElement>();

  public DomInvocationHandler(final Class<T> aClass, final XmlTag tag, final DomElement parent, @NotNull final String tagName) {
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

  public final XmlTag ensureTagExists() {
    if (getXmlTag() == null) {
      try {
        setXmlTag(getFile().getManager().getElementFactory().createTagFromText("<" + myTagName + "/>"));
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    return getXmlTag();
  }

  protected void setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    myParent.ensureTagExists().add(tag);
  }

  protected final String getTagName() {
    return myTagName;
  }

  private <T> T convertFromString(Method method, String s, final boolean getter) throws IllegalAccessException, InstantiationException {
    return ((Converter<T>)getConverter(method, getter)).fromString(s, new ConvertContext(getFile()));
  }

  private <T> String convertToString(Method method, T s, final boolean getter) throws IllegalAccessException, InstantiationException {
    return ((Converter<T>)getConverter(method, getter)).toString(s, new ConvertContext(getFile()));
  }

  public final DomElement getProxy() {
    return myProxy;
  }

  public final void setProxy(final DomElement proxy) {
    myProxy = proxy;
  }

  @NotNull
  private static Converter getConverter(Method method, final boolean getter) throws IllegalAccessException, InstantiationException {
    final Convert convert = method.getAnnotation(Convert.class);
    if (convert != null) {
      return convert.value().newInstance();
    }
    return getDefaultConverter(getter ? method.getReturnType() : method.getParameterTypes()[0]);
  }

  private static Converter getDefaultConverter(final Class<?> type) {
    if (type.equals(int.class) || type.equals(Integer.class)) {
      return Converter.INTEGER_CONVERTER;
    }
    if (isBoolean(type)) {
      return Converter.BOOLEAN_CONVERTER;
    }
    return Converter.EMPTY_CONVERTER;
  }

  private static boolean isBoolean(final Class<?> type) {
    return type.equals(boolean.class) || type.equals(Boolean.class);
  }

  @NotNull
  protected XmlFile getFile() {
    if (myFile == null) {
      myFile = getRoot().getFile();
    }
    return myFile;
  }

  public boolean equals(final Object o) {
    if (o instanceof DomInvocationHandler) {
      return myProxy.equals(((DomInvocationHandler)o).myProxy);
    }
    return false;
  }

  public int hashCode() {
    return myProxy.hashCode();
  }

  public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    final String name = method.getName();
    if (isCoreMethod(method)) {
      if ("equals".equals(name)) {
        return getProxy() == args[0];
      }
      return method.invoke(this, args);
    }

    boolean getter = isGetter(method);
    boolean setter = isSetter(method);

    final AttributeValue attributeValue = method.getAnnotation(AttributeValue.class);
    final XmlTag tag = getXmlTag();
    if (attributeValue != null) {
      return tag != null ? convertFromString(method, tag.getAttributeValue(guessName(attributeValue.value(), method)), true) : null;
    }

    final TagValue tagValue = method.getAnnotation(TagValue.class);
    if (getter && (tagValue != null || "getValue".equals(name))) {
      return tag != null ? convertFromString(method, tag.getValue().getText(), true) : null;
    }
    else if (setter && (tagValue != null || "setValue".equals(method.getName()))) {
      ensureTagExists().getValue().setText(convertToString(method, args[0], false));
      return null;
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
          final DomElement element = DomManagerImpl.getCachedElement(subTag);
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
          final DomElement element = DomManagerImpl.getCachedElement(subTags[i]);
          assert element != null : "Null annotated element for " + tag.getText() + "; " + qname + "; " + i;
          elements[i] = element;
        }
        return Arrays.asList(elements);
      }
    }

    throw new UnsupportedOperationException("Cannot call " + method.toString());
  }

  private static boolean isSetter(final Method method) {
    return method.getName().startsWith("set") && method.getParameterTypes().length == 1 && method.getReturnType() == void.class;
  }

  private static boolean isGetter(final Method method) {
    final String name = method.getName();
    if (method.getParameterTypes().length != 0) {
      return false;
    }
    final Class<?> returnType = method.getReturnType();
    if (name.startsWith("get")) {
      return returnType != void.class;
    }
    if (name.startsWith("is")) {
      return isBoolean(returnType);
    }
    return false;
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
        myChildren.put(method, DomManagerImpl.createXmlAnnotatedElement((Class<DomElement>)returnType, subTag, getProxy(), qname));
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
              DomManagerImpl.createXmlAnnotatedElement(aClass, subTag, getProxy(), qname);
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
    return DomManagerImpl._getNameStrategy(getFile());
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

  protected XmlTag restoreTag(String tagName) {
    final XmlTag tag = myParent.getXmlTag();
    return tag == null ? null : tag.findFirstSubTag(myTagName);
  }

  @Nullable
  public final XmlTag getXmlTag() {
    if (myTag == null) {
      myTag = restoreTag(myTagName);
      synchronized (PsiLock.LOCK) {
        DomManagerImpl.setCachedElement(myTag, getProxy());
      }
    }
    return myTag;
  }
}
