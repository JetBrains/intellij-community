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
  private final DomManagerImpl myManager;
  @NotNull private final String myTagName;
  private XmlTag myTag;

  private XmlFile myFile;
  private DomElement myProxy;
  private boolean myInitialized = false;
  private boolean myInitializing = false;
  private final Map<Method, DomElement> myMethod2Children = new HashMap<Method, DomElement>();
  private final Map<String, DomElement> myName2Children = new HashMap<String, DomElement>();
  private final Set<String> myCollectionChildrenNames = new HashSet<String>();

  public DomInvocationHandler(final Class<T> aClass, final XmlTag tag, final DomElement parent, @NotNull final String tagName, DomManagerImpl manager) {
    myClass = aClass;
    myTag = tag;
    myParent = parent;
    myTagName = tagName;
    myManager = manager;
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
    return _ensureTagExists(true);
  }

  private XmlTag _ensureTagExists(final boolean fireEvent) {
    if (getXmlTag() == null) {
      try {
        setXmlTag(getFile().getManager().getElementFactory().createTagFromText("<" + myTagName + "/>"));
        if (fireEvent) {
          myManager.fireEvent(new ElementDefinedEvent(this));
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    return getXmlTag();
  }

  public void undefine() {
    final XmlTag tag = getXmlTag();
    if (tag != null) {
      try {
        tag.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      myTag = null;
      myManager.fireEvent(new ElementUndefinedEvent(this));
    }
  }

  protected void setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    myParent.ensureTagExists().add(tag);
  }

  protected final String getTagName() {
    return myTagName;
  }

  private Object convertFromString(Method method, String s, final boolean getter) throws IllegalAccessException, InstantiationException {
    if (s == null) return null;
    return getConverter(method, getter).fromString(s, new ConvertContext(getFile()));
  }

  @NotNull
  private Converter getConverter(final Method method, final boolean getter) throws IllegalAccessException, InstantiationException {
    return myManager.getConverterManager().getConverter(method, getter);
  }

  private String convertToString(Method method, Object argument, final boolean getter) throws IllegalAccessException, InstantiationException {
    if (argument == null) return null;
    return getConverter(method, getter).toString(argument, new ConvertContext(getFile()));
  }

  public final DomElement getProxy() {
    return myProxy;
  }

  public final void setProxy(final DomElement proxy) {
    myProxy = proxy;
  }

  @NotNull
  protected XmlFile getFile() {
    if (myFile == null) {
      myFile = getRoot().getFile();
    }
    return myFile;
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

    XmlTag tag = getXmlTag();

    final TagValue tagValue = method.getAnnotation(TagValue.class);
    if (getter && (tagValue != null || "getValue".equals(name))) {
      return getValue(tag, method);
    }
    else if (setter && (tagValue != null || "setValue".equals(method.getName()))) {
      tag = ensureTagExists();
      final Object oldValue = getTagValue(tag);
      final Object newValue = args[0];
      setTagValue(tag, convertToString(method, newValue, false));
      myManager.fireEvent(new ValueChangeEvent(this, oldValue, newValue == null ? "" : newValue));
      return null;
    }

    checkInitialized();

    if (isCopyFrom(name, method)) {
      String qname = getTagNameFromCopyFromMethod(method);
      if (!StringUtil.isEmpty(qname)) {
        copyFrom((DomElement)args[0]);
        return null;
      }
    }

    if (myMethod2Children.containsKey(method)) {
      return myMethod2Children.get(method);
    }
    final Class<?> returnType = method.getReturnType();
    if (tag != null && DomElement.class.isAssignableFrom(returnType)) {
      final String qname = getSubTagName(method);
      if (qname != null) {
        XmlTag subTag = tag.findFirstSubTag(qname);
        if (subTag != null) {
          final DomElement element = DomManagerImpl.getCachedElement(subTag);
          if (element != null) {
            myMethod2Children.put(method, element);
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

  static boolean isBoolean(final Class<?> type) {
    return type.equals(boolean.class) || type.equals(Boolean.class);
  }

  private void copyFrom(final DomElement element) throws IncorrectOperationException {
    final XmlTag tag;
    tag = _ensureTagExists(false);
    final XmlTag newTag = element.ensureTagExists();
    List<DomEvent> events = new ArrayList<DomEvent>();
    if (!getTagValue(tag).equals(getTagValue(newTag))) {
      events.add(new ValueChangeEvent(this, getTagValue(tag), getTagValue(newTag)));
    }
    for (XmlTag subTag : tag.getSubTags()) {
      if (myName2Children.containsKey(subTag)) {
        events.add(new ElementUndefinedEvent(DomManagerImpl.getCachedElement(subTag)));
      }
    }
    for (XmlTag subTag : newTag.getSubTags()) {
      final DomElement child = myName2Children.get(subTag.getName());
      if (child != null) {
        events.add(new ElementDefinedEvent(child));
        events.add(new ElementChangedEvent(child));
      }
    }

    myTag = (XmlTag) tag.replace(newTag);
    for (DomEvent event : events) {
      myManager.fireEvent(event);
    }
  }

  private void setTagValue(final XmlTag tag, final String value) {
    tag.getValue().setText(value);
  }

  private String getTagNameFromCopyFromMethod(final Method method) {
    final SubTag subTag = method.getAnnotation(SubTag.class);
    if (subTag != null && !StringUtil.isEmpty(subTag.value())) {
      return subTag.value();
    }

    final String name = method.getName();
    return getNameStrategy().convertName(name.substring("copy".length(), name.length() - "From".length()));
  }

  private boolean isCopyFrom(final String name, final Method method) {
    if (!name.startsWith("copy") || !name.endsWith("From")) {
      return false;
    }
    final Class<?>[] parameterTypes = method.getParameterTypes();
    if (parameterTypes.length != 1 || !DomElement.class.isAssignableFrom(parameterTypes[0])) {
      return false;
    }
    return void.class.equals(method.getReturnType());
  }

  private Object getValue(final XmlTag tag, final Method method) throws IllegalAccessException, InstantiationException {
    return tag != null ? convertFromString(method, getTagValue(tag), true) : null;
  }

  private String getTagValue(final XmlTag tag) {
    return tag.getValue().getText();
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
    return StringUtil.getShortName(myClass) + " @" + hashCode();
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
        final DomElement element = myManager.createDomElement((Class<DomElement>)returnType, subTag, getProxy(), qname);
        myName2Children.put(qname, element);
        myMethod2Children.put(method, element);
        tags.add(subTag);
        return;
      }
    }
    if (tag != null) {
      final Class<? extends DomElement> aClass = extractElementType(method.getGenericReturnType());
      if (aClass != null) {
        final String qname = getSubTagNameForCollection(method);
        if (qname != null) {
          myCollectionChildrenNames.add(qname);
          for (int i = 0; i < tag.findSubTags(qname).length; i++) {
            XmlTag subTag = tag.findSubTags(qname)[i];
            if (!tags.contains(subTag)) {
              myManager.createDomElement(aClass, subTag, getProxy(), qname);
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
