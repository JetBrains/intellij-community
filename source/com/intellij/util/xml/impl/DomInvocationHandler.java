/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiLock;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.CollectionElementAddedEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.events.ElementUndefinedEvent;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @author peter
 */
public abstract class DomInvocationHandler implements InvocationHandler, DomElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomInvocationHandler");

  private final Type myType;
  private final DomInvocationHandler myParent;
  private final DomManagerImpl myManager;
  @NotNull private final String myTagName;
  private XmlTag myXmlTag;

  private XmlFile myFile;
  private DomElement myProxy;
  private boolean myInitialized = false;
  private boolean myInitializing = false;
  private final Map<Pair<String, Integer>, IndexedElementInvocationHandler> myFixedChildren = new HashMap<Pair<String, Integer>, IndexedElementInvocationHandler>();
  private final MethodsMap myMethodsMap;
  private boolean myInvalidated;
  private InvocationCache myInvocationCache;

  protected DomInvocationHandler(final Type type,
                                 final XmlTag tag,
                                 final DomInvocationHandler parent,
                                 final String tagName,
                                 DomManagerImpl manager) {
    myType = type;
    myXmlTag = tag;
    myParent = parent;
    myTagName = tagName;
    myManager = manager;
    myMethodsMap = manager.getMethodsMap(type);
    myInvocationCache = manager.getInvocationCache(type);
  }

  public DomFileElementImpl getRoot() {
    return isValid() ? myParent.getRoot() : null;
  }

  public DomElement getParent() {
    return isValid() ? myParent.getProxy() : null;
  }

  final DomInvocationHandler getParentHandler() {
    return myParent;
  }

  final void invalidate() {
    myInvalidated = true;
  }

  IndexedElementInvocationHandler getFixedChild(String tagName, int index) {
    return myFixedChildren.get(new Pair<String, Integer>(tagName, index));
  }

  public final XmlTag ensureTagExists() {
    if (myXmlTag != null) return myXmlTag;

    final boolean changing = myManager.setChanging(true);
    try {
      cacheDomElement(setXmlTag(createEmptyTag()));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    catch (InstantiationException e) {
      LOG.error(e);
    }
    finally {
      myManager.setChanging(changing);
      myManager.fireEvent(new ElementDefinedEvent(this));
    }
    return myXmlTag;
  }

  protected final XmlTag createEmptyTag() throws IncorrectOperationException {
    return createEmptyTag(myTagName);
  }

  protected final XmlTag createEmptyTag(final String tagName) throws IncorrectOperationException {
    return getFile().getManager().getElementFactory().createTagFromText("<" + tagName + "/>");
  }

  public final boolean isValid() {
    if (!myInvalidated && myXmlTag != null && !myXmlTag.isValid()) {
      myInvalidated = true;
    }
    return !myInvalidated;
  }

  public final MethodsMap getMethodsInfo() {
    myMethodsMap.buildMethodMaps(getFile());
    return myMethodsMap;
  }

  public int getChildIndex(final DomElement child) {
    for (Map.Entry<Pair<String, Integer>, IndexedElementInvocationHandler> entry : myFixedChildren.entrySet()) {
      if (entry.getValue().getProxy().equals(child)) {
        return entry.getKey().getSecond();
      }
    }
    return -1;
  }

  public void undefine() throws IllegalAccessException, InstantiationException {
    final XmlTag tag = getXmlTag();
    if (tag != null) {
      deleteTag(tag);
      fireUndefinedEvent();
    }
  }

  protected final void deleteTag(final XmlTag tag) {
    final boolean changing = myManager.setChanging(true);
    try {
      tag.delete();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    } finally {
      myManager.setChanging(changing);
    }
    myXmlTag = null;
  }

  protected final void fireUndefinedEvent() {
    myManager.fireEvent(new ElementUndefinedEvent(getProxy()));
  }

  protected final void fireDefinedEvent() {
    myManager.fireEvent(new ElementDefinedEvent(getProxy()));
  }

  protected abstract XmlTag setXmlTag(final XmlTag tag) throws IncorrectOperationException, IllegalAccessException, InstantiationException;

  public final String getTagName() {
    return myTagName;
  }

  @NotNull
  protected Converter getConverter(final Method method, final boolean getter) throws IllegalAccessException, InstantiationException {
    return myManager.getConverterManager().getConverter(method, getter);
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

  @Nullable
  private String guessAttributeName(Method method) {
    final AttributeValue attributeValue = method.getAnnotation(AttributeValue.class);
    if (attributeValue == null) return null;

    if (StringUtil.isNotEmpty(attributeValue.value())) {
      return attributeValue.value();
    }
    final String propertyName = PropertyUtil.getPropertyName(method.getName());
    if (StringUtil.isEmpty(propertyName)) {
      return null;
    }

    return myManager.getNameStrategy(getFile()).convertName(propertyName);
  }

  private Invocation createInvocation(final Method method) throws IllegalAccessException, InstantiationException {
    boolean getter = isGetter(method);
    boolean setter = method.getName().startsWith("set") && method.getParameterTypes().length == 1 && method.getReturnType() == void.class;

    final String attributeName = guessAttributeName(method);
    if (attributeName != null) {
      final Converter converter = getConverter(method, getter);
      if (getter) {
        return new GetAttributeValueInvocation(converter, attributeName);
      } else if (setter) {
        return new SetAttributeValueInvocation(converter, attributeName);
      }
    }

    final TagValue tagValue = method.getAnnotation(TagValue.class);
    if (getter && (tagValue != null || "getValue".equals(method.getName()))) {
      return new GetValueInvocation(getConverter(method, true));
    }
    else if (setter && (tagValue != null || "setValue".equals(method.getName()))) {
      return new SetValueInvocation(getConverter(method, false));
    }

    return myMethodsMap.createInvocation(getFile(), method);
  }

  @NotNull
  final DomInvocationHandler getFixedChild(final Method method) {
    final DomInvocationHandler domElement = myFixedChildren.get(myMethodsMap.getFixedChildInfo(method));
    assert domElement != null : method.toString();
    return domElement;
  }

  public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    Invocation invocation = myInvocationCache.getInvocation(method);
    if (invocation == null) {
      invocation = createInvocation(method);
      myInvocationCache.putInvocation(method, invocation);
    }
    return invocation.invoke(this, args);
  }

  static void setTagValue(final XmlTag tag, final String value) {
    tag.getValue().setText(value);
  }

  static String getTagValue(final XmlTag tag) {
    return tag.getValue().getText();
  }

  static boolean isGetter(final Method method) {
    final String name = method.getName();
    if (method.getParameterTypes().length != 0) {
      return false;
    }
    final Class<?> returnType = method.getReturnType();
    if (name.startsWith("get")) {
      return returnType != void.class;
    }
    if (name.startsWith("is")) {
      return returnType.equals(boolean.class) || returnType.equals(Boolean.class);
    }
    return false;
  }

  public String toString() {
    return StringUtil.getShortName(myType.toString()) + " @" + hashCode();
  }

  final void checkInitialized() throws IllegalAccessException, InstantiationException {
    synchronized (PsiLock.LOCK) {
      if (myInitialized || myInitializing) return;
      myInitializing = true;
      try {
        final HashSet<XmlTag> usedTags = new HashSet<XmlTag>();

        final XmlTag tag = getXmlTag();
        for (Map.Entry<Method, Pair<String, Integer>> entry : myMethodsMap.getFixedChildrenEntries()) {
          final Pair<String, Integer> pair = entry.getValue();
          final String qname = pair.getFirst();
          final Integer index = pair.getSecond();
          XmlTag subTag = findSubTag(tag, qname, index);
          final Method method = entry.getKey();
          final IndexedElementInvocationHandler handler = createIndexedChild(method, subTag, qname, index);
          myManager.createDomElement(method.getReturnType(), subTag, handler);
          myFixedChildren.put(pair, handler);
          usedTags.add(subTag);
        }

        if (tag != null) {
          for (Map.Entry<Method, String> entry : myMethodsMap.getCollectionChildrenEntries()) {
            String qname = entry.getValue();
            for (XmlTag subTag : tag.findSubTags(qname)) {
              if (!usedTags.contains(subTag)) {
                createCollectionElement(myMethodsMap.getCollectionChildrenClass(qname), subTag);
                usedTags.add(subTag);
              }
            }
          }
        }
      }
      finally {
        myInitializing = false;
        myInitialized = true;
      }
    }
  }

  private IndexedElementInvocationHandler createIndexedChild(final Method method,
                                                             final XmlTag subTag,
                                                             final String qname, final Integer index) throws
                                                                                                      InstantiationException,
                                                                                                      IllegalAccessException {
    Converter converter = getConverterForChild(method);
    if (converter != null) {
      return new GenericValueInvocationHandler(method.getGenericReturnType(), subTag, this, qname, index, converter);
    }
    return new IndexedElementInvocationHandler(method.getReturnType(), subTag, this, qname, index);
  }

  private Converter getConverterForChild(final Method method) throws InstantiationException, IllegalAccessException {
    final Type genericReturnType = method.getGenericReturnType();
    if (genericReturnType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType)genericReturnType;
      if (parameterizedType.getRawType() == GenericValue.class) {
        final Convert convertAnnotation = method.getAnnotation(Convert.class);
        if (convertAnnotation != null) {
          return myManager.getConverterManager().getConverter(convertAnnotation.value());
        }

        final Type[] arguments = parameterizedType.getActualTypeArguments();
        if (arguments.length == 1 && arguments[0] instanceof Class) {
          return myManager.getConverterManager().getConverter((Class)arguments[0]);
        }
      }
    }
    return null;
  }

  DomElement createCollectionElement(final Class aClass, final XmlTag subTag) {
    return myManager.createDomElement(aClass, subTag, new CollectionElementInvocationHandler(aClass, subTag, this));
  }

  protected final XmlTag findSubTag(final XmlTag tag, final String qname, final int index) {
    if (tag == null) {
      return null;
    }
    final XmlTag[] subTags = tag.findSubTags(qname);
    return subTags.length <= index ? null : subTags[index];
  }

  @Nullable
  public final XmlTag getXmlTag() {
    return myXmlTag;
  }

  protected final void cacheDomElement(final XmlTag tag) {
    synchronized (PsiLock.LOCK) {
      myXmlTag = tag;
      DomManagerImpl.setCachedElement(tag, this);
    }
  }

  public final DomManagerImpl getManager() {
    return myManager;
  }

  protected final void createFixedChildrenTags(String tagName, int count) throws IncorrectOperationException, IllegalAccessException,
                                                                                 InstantiationException {
    checkInitialized();
    final XmlTag tag = ensureTagExists();
    for (Map.Entry<Method, Pair<String, Integer>> entry : myMethodsMap.getFixedChildrenEntries()) {
      final Pair<String, Integer> pair = entry.getValue();
      if (tagName.equals(pair.getFirst()) && pair.getSecond() < count) {
        getFixedChild(entry.getKey()).ensureTagExists();
      }
    }

    final int existing = tag.findSubTags(tagName).length;
    for (int i = existing; i < count; i++) {
      tag.add(createEmptyTag(tagName));
    }
  }

  public final DomElement addChild(final String tagName, final Class aClass, int index) throws IncorrectOperationException,
                                                                                               IllegalAccessException,
                                                                                               InstantiationException {
    createFixedChildrenTags(tagName, myMethodsMap.getFixedChildrenCount(tagName));
    return addCollectionElement(aClass, addEmptyTag(tagName, index));
  }

  private DomElement addCollectionElement(final Class aClass, final XmlTag tag) {
    final DomElement element = createCollectionElement(aClass, tag);
    myManager.fireEvent(new CollectionElementAddedEvent(element, tag.getName()));
    return element;
  }

  private XmlTag addEmptyTag(final String tagName, int index) throws IncorrectOperationException {
    final XmlTag tag = ensureTagExists();
    final XmlTag[] subTags = tag.findSubTags(tagName);
    if (subTags.length < index) {
      index = subTags.length;
    }
    final boolean changing = myManager.setChanging(true);
    try {
      XmlTag newTag = createEmptyTag(tagName);
      if (index == 0) {
        return (XmlTag)tag.add(newTag);
      }

      return (XmlTag)tag.addAfter(newTag, subTags[index - 1]);
    }
    finally {
      myManager.setChanging(changing);
    }
  }

  public boolean isInitialized() {
    synchronized (PsiLock.LOCK) {
      return myInitialized;
    }
  }

}
