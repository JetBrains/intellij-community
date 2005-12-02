/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiLock;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
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

  private Type myType;
  private final DomInvocationHandler myParent;
  private final DomManagerImpl myManager;
  private final String myTagName;
  private final Converter myGenericConverter;
  private XmlTag myXmlTag;

  private XmlFile myFile;
  private DomElement myProxy;
  private boolean myInitialized = false;
  private final Map<Pair<String, Integer>, IndexedElementInvocationHandler> myFixedChildren = new HashMap<Pair<String, Integer>, IndexedElementInvocationHandler>();
  private final Map<String, AttributeChildInvocationHandler> myAttributeChildren = new HashMap<String, AttributeChildInvocationHandler>();
  private GenericInfoImpl myGenericInfoImpl;
  private final Map<String, Class> myFixedChildrenClasses = new HashMap<String, Class>();
  private final Map<Class, Object> myImplementations = new HashMap<Class, Object>();
  private boolean myInvalidated;
  private InvocationCache myInvocationCache;

  protected DomInvocationHandler(final Type type,
                                 final XmlTag tag,
                                 final DomInvocationHandler parent,
                                 final String tagName,
                                 final DomManagerImpl manager,
                                 final Converter genericConverter) {
    myXmlTag = tag;
    myParent = parent;
    myTagName = tagName;
    myManager = manager;
    myGenericConverter = genericConverter;
    setType(type);
  }

  private final void createImplementations(Class<?> interfaceClass) {
    final Implementation annotation = interfaceClass.getAnnotation(Implementation.class);
    if (annotation != null) {
      myImplementations.put(interfaceClass, createImplementation(annotation.value()));
    }
    for (Class aClass1 : interfaceClass.getInterfaces()) {
      createImplementations(aClass1);
    }
  }

  public DomFileElementImpl getRoot() {
    return isValid() ? myParent.getRoot() : null;
  }

  public DomElement getParent() {
    return isValid() ? myParent.getProxy() : null;
  }

  public void setType(final Type type) {
    myType = type;
    myGenericInfoImpl = myManager.getGenericInfo(type);
    myInvocationCache = myManager.getInvocationCache(type);
  }

  final DomInvocationHandler getParentHandler() {
    return myParent;
  }

  final IndexedElementInvocationHandler getFixedChild(String tagName, int index) {
    return myFixedChildren.get(new Pair<String, Integer>(tagName, index));
  }

  public final Type getDomElementType() {
    return myType;
  }

  public XmlTag ensureTagExists() {
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

  public final GenericInfoImpl getGenericInfo() {
    myGenericInfoImpl.buildMethodMaps();
    return myGenericInfoImpl;
  }

  public void undefine() {
    try {
      final XmlTag tag = getXmlTag();
      if (tag != null) {
        deleteTag(tag);
        fireUndefinedEvent();
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public final DomInvocationHandler getDomInvocationHandler() {
    return this;
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

  public final String getXmlElementName() {
    return myTagName;
  }

  public void accept(final DomElementVisitor visitor) {
    DomUtil.tryAccept(visitor, DomUtil.getRawType(myType), getProxy());
  }

  public final void acceptChildren(DomElementVisitor visitor) {
    for (DomInvocationHandler handler : getAllChildren()) {
      handler.accept(visitor);
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

  public Object getImplementation(Class interfaceClass) {
    checkInitialized();
    return myImplementations.get(interfaceClass);
  }

  @NotNull
  protected final XmlFile getFile() {
    if (myFile == null) {
      myFile = getRoot().getFile();
    }
    return myFile;
  }

  public final DomNameStrategy getNameStrategy() {
    final Class<?> rawType = DomUtil.getRawType(myType);
    final DomNameStrategy strategy = DomUtil.getDomNameStrategy(rawType);
    if (strategy != null) {
      return strategy;
    }
    final DomInvocationHandler parent = getParentHandler();
    return parent != null ? parent.getNameStrategy() : DomNameStrategy.HYPHEN_STRATEGY;
  }

  public final String getCommonPresentableName() {
    return StringUtil.capitalizeWords(getNameStrategy().splitIntoWords(getXmlElementName()), true);
  }

  public final GlobalSearchScope getResolveScope() {
    return getRoot().getResolveScope();
  }

  private <T extends DomElement> T _getParentOfType(Class<T> requiredClass, DomElement element) {
    while (element != null && !(requiredClass.isInstance(element))) {
      element = element.getParent();
    }
    return (T)element;
  }

  public final <T extends DomElement> T getParentOfType(Class<T> requiredClass, boolean strict) {
    return _getParentOfType(requiredClass, strict ? getParent() : getProxy());
  }

  protected final Invocation createInvocation(final Method method) throws IllegalAccessException, InstantiationException {
    if (DomUtil.isTagValueGetter(method)) {
      return createGetValueInvocation(getConverter(method, true));
    }

    if (DomUtil.isTagValueSetter(method)) {
      return createSetValueInvocation(getConverter(method, false));
    }

    return myGenericInfoImpl.createInvocation(method);
  }

  protected Invocation createSetValueInvocation(final Converter converter) {
    return new SetValueInvocation(converter);
  }

  protected Invocation createGetValueInvocation(final Converter converter) {
    return new GetValueInvocation(converter);
  }

  @NotNull
  final DomInvocationHandler getFixedChild(final MethodSignature method) {
    final DomInvocationHandler domElement = myFixedChildren.get(myGenericInfoImpl.getFixedChildInfo(method));
    assert domElement != null : method.toString();
    return domElement;
  }

  @NotNull
  final AttributeChildInvocationHandler getAttributeChild(final MethodSignature method) {
    final AttributeChildInvocationHandler domElement = myAttributeChildren.get(myGenericInfoImpl.getAttributeName(method));
    assert domElement != null : method.toString();
    return domElement;
  }

  public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    return doInvoke(MethodSignature.getSignature(method), args);
  }

  public final Object doInvoke(final MethodSignature signature, final Object... args) throws Throwable {
    Invocation invocation = myInvocationCache.getInvocation(signature);
    if (invocation == null) {
      invocation = createInvocation(signature.findMethod(myType));
      myInvocationCache.putInvocation(signature, invocation);
    }
    return invocation.invoke(this, args);
  }

  static void setTagValue(final XmlTag tag, final String value) {
    tag.getValue().setText(value);
  }

  static String getTagValue(final XmlTag tag) {
    final XmlText[] textElements = tag.getValue().getTextElements();
    return textElements.length != 0 ? textElements[0].getValue() : null;
  }

  public final String toString() {
    return myType.toString() + " @" + hashCode();
  }

  final void checkInitialized() {
    assert isValid();
    synchronized (PsiLock.LOCK) {
      if (myInitialized) return;
      try {
        myGenericInfoImpl.buildMethodMaps();
        for (Map.Entry<MethodSignature, String> entry : myGenericInfoImpl.getAttributeChildrenEntries()) {
          getOrCreateAttributeChild(entry.getKey().findMethod(myType), entry.getValue());
        }

        final HashSet<XmlTag> usedTags = new HashSet<XmlTag>();
        final XmlTag tag = getXmlTag();
        for (Map.Entry<MethodSignature, Pair<String, Integer>> entry : myGenericInfoImpl.getFixedChildrenEntries()) {
          final Pair<String, Integer> pair = entry.getValue();
          final XmlTag subTag = findSubTag(tag, pair.getFirst(), pair.getSecond());
          getOrCreateIndexedChild(entry.getKey().findMethod(myType), subTag, pair);
          usedTags.add(subTag);
        }

        if (tag != null) {
          for (Map.Entry<MethodSignature, String> entry : myGenericInfoImpl.getCollectionChildrenEntries()) {
            String qname = entry.getValue();
            for (XmlTag subTag : tag.findSubTags(qname)) {
              if (!usedTags.contains(subTag)) {
                createCollectionElement(myGenericInfoImpl.getCollectionChildrenType(qname), subTag);
                usedTags.add(subTag);
              }
            }
          }
        }

        createImplementations(DomUtil.getRawType(myType));
      }
      finally {
        myInitialized = true;
      }
    }
  }

  private final Object createImplementation(Class aClass) {
    try {
      return aClass.getConstructor().newInstance();
    }
    catch (NoSuchMethodException e) {
      try {
        return aClass.getConstructor(DomElement.class).newInstance(myProxy);
      }
      catch (Exception e1) {
        throw new AssertionError(aClass.getName() + " should have either empty or <init>(DomElement) constructor");
      }
    }
    catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private void getOrCreateAttributeChild(final Method method, final String attributeName) {
    final AttributeChildInvocationHandler handler = new AttributeChildInvocationHandler(method.getGenericReturnType(),
                                                                                        getXmlTag(),
                                                                                        this,
                                                                                        attributeName,
                                                                                        myManager,
                                                                                        getConverterForChild(method));
    myManager.createDomElement(handler);
    myAttributeChildren.put(handler.getXmlElementName(), handler);
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
    Type type = method.getGenericReturnType();
    if (myFixedChildrenClasses.containsKey(qname)) {
      type = getFixedChildrenClass(qname);
    }
    return new IndexedElementInvocationHandler(type, subTag, this, qname, index, converter);
  }

  protected final Class getFixedChildrenClass(final String tagName) {
    return myFixedChildrenClasses.get(tagName);
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
  public XmlTag getXmlTag() {
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
      removeFromCache();
      myXmlTag = null;
    }
  }

  protected void removeFromCache() {
    DomManagerImpl.setCachedElement(myXmlTag, null);
  }

  protected final void attach(final XmlTag tag) {
    synchronized (PsiLock.LOCK) {
      myXmlTag = tag;
      cacheInTag(tag);
    }
  }

  protected void cacheInTag(final XmlTag tag) {
    DomManagerImpl.setCachedElement(tag, this);
  }

  public final DomManagerImpl getManager() {
    return myManager;
  }

  protected final void createFixedChildrenTags(String tagName, int count) throws IncorrectOperationException {
    checkInitialized();
    final XmlTag tag = ensureTagExists();
    for (Map.Entry<MethodSignature, Pair<String, Integer>> entry : myGenericInfoImpl.getFixedChildrenEntries()) {
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
    createFixedChildrenTags(tagName, myGenericInfoImpl.getFixedChildrenCount(tagName));
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

  public final boolean isInitialized() {
    synchronized (PsiLock.LOCK) {
      return myInitialized;
    }
  }

  public void setFixedChildClass(final String tagName, final Class<? extends DomElement> aClass) {
    synchronized (PsiLock.LOCK) {
      assert !myInitialized;
      myFixedChildrenClasses.put(tagName, aClass);
    }
  }
}

