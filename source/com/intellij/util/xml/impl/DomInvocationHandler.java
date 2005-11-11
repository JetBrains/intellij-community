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
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public abstract class DomInvocationHandler implements InvocationHandler, DomElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomInvocationHandler");

  private final Type myType;
  private final DomInvocationHandler myParent;
  private final DomManagerImpl myManager;
  private final String myTagName;
  private final Converter myGenericConverter;
  private XmlTag myXmlTag;

  private XmlFile myFile;
  private DomElement myProxy;
  private boolean myInitialized = false;
  private final Map<Pair<String, Integer>, IndexedElementInvocationHandler> myFixedChildren = new HashMap<Pair<String, Integer>, IndexedElementInvocationHandler>();
  private final MethodsMap myMethodsMap;
  private boolean myInvalidated;
  private InvocationCache myInvocationCache;

  protected DomInvocationHandler(final Type type,
                                 final XmlTag tag,
                                 final DomInvocationHandler parent,
                                 final String tagName,
                                 DomManagerImpl manager, final Converter genericConverter) {
    myType = type;
    myXmlTag = tag;
    myParent = parent;
    myTagName = tagName;
    myManager = manager;
    myMethodsMap = manager.getMethodsMap(type);
    myInvocationCache = manager.getInvocationCache(type);
    myGenericConverter = genericConverter;
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

  IndexedElementInvocationHandler getFixedChild(String tagName, int index) {
    return myFixedChildren.get(new Pair<String, Integer>(tagName, index));
  }

  public final Type getDomElementType() {
    return myType;
  }

  public final XmlTag ensureTagExists() {
    if (myXmlTag != null) return myXmlTag;

    final boolean changing = myManager.setChanging(true);
    try {
      attach(setXmlTag(createEmptyTag()));
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

  public void acceptChildren(DomElementVisitor visitor) {
    for (DomInvocationHandler handler : getAllChildren()) {
      visitor.visitDomElement(handler.getProxy());
    }
  }

  private Collection<DomInvocationHandler> getAllChildren() {
    checkInitialized();
    Set<DomInvocationHandler> result = new HashSet<DomInvocationHandler>(myFixedChildren.values());
    result.addAll(getCollectionChildren(result));
    return result;
  }

  private List<CollectionElementInvocationHandler> getCollectionChildren(final Set<DomInvocationHandler> fixedChildren) {
    final List<CollectionElementInvocationHandler> collectionChildren = new ArrayList<CollectionElementInvocationHandler>();
    final XmlTag tag = getXmlTag();
    if (tag != null) {
      for (XmlTag xmlTag : tag.getSubTags()) {
        final DomInvocationHandler cachedElement = DomManagerImpl.getCachedElement(xmlTag);
        if (cachedElement != null && !fixedChildren.contains(cachedElement)) {
          collectionChildren.add((CollectionElementInvocationHandler)cachedElement);
        }
      }
    }
    return collectionChildren;
  }

  @NotNull
  protected final Converter getConverter(final Method method, final boolean getter) throws IllegalAccessException, InstantiationException {
    return myManager.getConverterManager().getConverter(method, getter, myType, myGenericConverter);
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

  final void checkInitialized() {
    synchronized (PsiLock.LOCK) {
      if (myInitialized) return;
      try {
        final HashSet<XmlTag> usedTags = new HashSet<XmlTag>();

        final XmlTag tag = getXmlTag();
        for (Map.Entry<Method, Pair<String, Integer>> entry : myMethodsMap.getFixedChildrenEntries()) {
          final Pair<String, Integer> pair = entry.getValue();
          final XmlTag subTag = findSubTag(tag, pair.getFirst(), pair.getSecond());
          getOrCreateIndexedChild(entry.getKey(), subTag, pair);
          usedTags.add(subTag);
        }

        if (tag != null) {
          for (Map.Entry<Method, String> entry : myMethodsMap.getCollectionChildrenEntries()) {
            String qname = entry.getValue();
            for (XmlTag subTag : tag.findSubTags(qname)) {
              if (!usedTags.contains(subTag)) {
                createCollectionElement(myMethodsMap.getCollectionChildrenType(qname), subTag);
                usedTags.add(subTag);
              }
            }
          }
        }
      }
      finally {
        myInitialized = true;
      }
    }
  }

  private IndexedElementInvocationHandler getOrCreateIndexedChild(final Method method, final XmlTag subTag, final Pair<String, Integer> pair) {
    IndexedElementInvocationHandler handler = myFixedChildren.get(pair);
    if (handler == null) {
      handler = createIndexedChild(method, subTag, pair.getFirst(), pair.getSecond());
      myFixedChildren.put(pair, handler);
      myManager.createDomElement(handler);
    } else {
      handler.attach(subTag);
    }
    return handler;
  }

  private IndexedElementInvocationHandler createIndexedChild(final Method method,
                                                             final XmlTag subTag,
                                                             final String qname,
                                                             final Integer index) {
    Converter converter = getConverterForChild(method);
    return new IndexedElementInvocationHandler(method.getGenericReturnType(), subTag, this, qname, index, converter);
  }

  private Converter getConverterForChild(final Method method) {
    try {
      final Class aClass = DomUtil.extractParameterClassFromGenericType(method.getGenericReturnType());
      if (aClass == null) return null;

      final Convert convertAnnotation = method.getAnnotation(Convert.class);
      if (convertAnnotation != null) {
        return myManager.getConverterManager().getConverter(convertAnnotation.value());
      }

      return myManager.getConverterManager().getConverter(aClass);
    }
    catch (InstantiationException e) {
      LOG.error(e);
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
    return null;
  }

  final DomElement createCollectionElement(final Type type, final XmlTag subTag) {
    return myManager.createDomElement(new CollectionElementInvocationHandler(type, subTag, this));
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

  protected final void detach(boolean invalidate) {
    synchronized (PsiLock.LOCK) {
      if (myXmlTag == null) return;
      myInvalidated = invalidate;
      if (myInitialized) {
        Set<DomInvocationHandler> fixedChildren = new HashSet<DomInvocationHandler>(myFixedChildren.values());
        for (DomInvocationHandler handler : fixedChildren) {
          handler.detach(invalidate);
        }
        for (CollectionElementInvocationHandler handler : getCollectionChildren(fixedChildren)) {
          handler.detach(true);
        }
      }

      myInitialized = false;
      DomManagerImpl.setCachedElement(myXmlTag, null);
      myXmlTag = null;
    }
  }

  protected final void attach(@NotNull final XmlTag tag) {
    synchronized (PsiLock.LOCK) {
      myXmlTag = tag;
      DomManagerImpl.setCachedElement(tag, this);
    }
  }

  public final DomManagerImpl getManager() {
    return myManager;
  }

  protected final void createFixedChildrenTags(String tagName, int count) throws IncorrectOperationException {
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

  public final DomElement addChild(final String tagName, final Type type, int index) throws IncorrectOperationException {
    createFixedChildrenTags(tagName, myMethodsMap.getFixedChildrenCount(tagName));
    return addCollectionElement(type, addEmptyTag(tagName, index));
  }

  private DomElement addCollectionElement(final Type type, final XmlTag tag) {
    final DomElement element = createCollectionElement(type, tag);
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
