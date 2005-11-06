/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
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
import java.util.*;

/**
 * @author peter
 */
abstract class DomInvocationHandler<T extends DomElement> implements InvocationHandler, DomElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.DomInvocationHandler");
  private static final Map<Method, Invocation> ourInvocations = new HashMap<Method, Invocation>();

  private final Class<T> myClass;
  private final DomInvocationHandler myParent;
  private final DomManagerImpl myManager;
  @NotNull private final String myTagName;
  protected XmlTag myXmlTag;

  private XmlFile myFile;
  private DomElement myProxy;
  private boolean myInitialized = false;
  private boolean myInitializing = false;
  private final Map<Method, DomElement> myMethod2Children = new HashMap<Method, DomElement>();
  private final MethodsMap myMethodsMap;


  static {
    for (final Method method : DomElement.class.getMethods()) {
      ourInvocations.put(method, new Invocation() {
        public Object invoke(DomInvocationHandler handler, Object[] args) throws Throwable {
          return method.invoke(handler, args);
        }
      });
    }
    for (final Method method : Object.class.getMethods()) {
      if ("equals".equals(method.getName())) {
        ourInvocations.put(method, new Invocation() {
          public Object invoke(DomInvocationHandler handler, Object[] args) throws Throwable {
            return handler.getProxy() == args[0];
          }
        });
      }
      else {
        ourInvocations.put(method, new Invocation() {
          public Object invoke(DomInvocationHandler handler, Object[] args) throws Throwable {
            return method.invoke(handler, args);
          }
        });
      }
    }
  }


  public DomInvocationHandler(final Class<T> aClass,
                              final XmlTag tag,
                              final DomInvocationHandler parent,
                              @NotNull final String tagName,
                              DomManagerImpl manager) {
    myClass = aClass;
    myXmlTag = tag;
    myParent = parent;
    myTagName = tagName;
    myManager = manager;
    myMethodsMap = manager.getMethodsMap(aClass);
  }

  @NotNull
  public DomFileElement getRoot() {
    return myParent.getRoot();
  }

  public DomElement getParent() {
    return myParent.getProxy();
  }

  public DomInvocationHandler getParentHandler() {
    return myParent;
  }

  public final XmlTag ensureTagExists() {
    return _ensureTagExists(true);
  }

  private XmlTag _ensureTagExists(final boolean fireEvent) {
    if (getXmlTag() == null) {
      try {
        setXmlTag(createEmptyTag());
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

  protected final XmlTag createEmptyTag() throws IncorrectOperationException {
    return createEmptyTag(myTagName);
  }

  protected final XmlTag createEmptyTag(final String tagName) throws IncorrectOperationException {
    return getFile().getManager().getElementFactory().createTagFromText("<" + tagName + "/>");
  }

  public boolean isValid() {
    return true;
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
      myXmlTag = null;
      fireUndefinedEvent();
    }
  }

  protected final void fireUndefinedEvent() {
    myManager.fireEvent(new ElementUndefinedEvent(this));
  }

  protected abstract void setXmlTag(final XmlTag tag) throws IncorrectOperationException;

  protected final String getTagName() {
    return myTagName;
  }

  @NotNull
  private Converter getConverter(final Method method, final boolean getter) throws IllegalAccessException, InstantiationException {
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
    boolean setter = isSetter(method);

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
  final DomElement getFixedChild(final Method method) {
    final DomElement domElement = myMethod2Children.get(method);
    assert domElement != null : method.toString();
    return domElement;
  }

  protected final MethodsMap getMethodsMap() {
    return myMethodsMap;
  }

  public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    Invocation invocation = ourInvocations.get(method);
    if (invocation == null) {
      invocation = createInvocation(method);
      ourInvocations.put(method, invocation);
    }
    return invocation.invoke(this, args);
  }

  static boolean isBoolean(final Class<?> type) {
    return type.equals(boolean.class) || type.equals(Boolean.class);
  }

  static void setTagValue(final XmlTag tag, final String value) {
    tag.getValue().setText(value);
  }

  static String getTagValue(final XmlTag tag) {
    return tag.getValue().getText();
  }

  private static boolean isSetter(final Method method) {
    return method.getName().startsWith("set") && method.getParameterTypes().length == 1 && method.getReturnType() == void.class;
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
      return isBoolean(returnType);
    }
    return false;
  }

  public String toString() {
    return StringUtil.getShortName(myClass) + " @" + hashCode();
  }

  void checkInitialized() {
    synchronized (PsiLock.LOCK) {
      if (myInitialized || myInitializing) return;
      myInitializing = true;
      try {
        final HashSet<XmlTag> usedTags = new HashSet<XmlTag>();

        final XmlTag tag = getXmlTag();
        for (Map.Entry<Method, Pair<String, Integer>> entry : myMethodsMap.getFixedChildrenEntries()) {
          Method method = entry.getKey();
          final String qname = entry.getValue().getFirst();
          final Integer index = entry.getValue().getSecond();
          XmlTag subTag = findSubTag(tag, qname, index);
          final Class aClass = method.getReturnType();
          final IndexedElementInvocationHandler handler = new IndexedElementInvocationHandler(aClass, subTag, this, qname, index);
          final DomElement element = myManager.createDomElement(aClass, subTag, handler);
          myMethod2Children.put(method, element);
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

  private DomElement createCollectionElement(final Class aClass, final XmlTag subTag) {
    return myManager.createDomElement(aClass, subTag, new CollectionElementInvocationHandler(aClass, subTag, this));
  }

  protected final XmlTag findSubTag(final XmlTag tag, final String qname, final int index) {
    if (tag == null) {
      return null;
    }
    final XmlTag[] subTags = tag.findSubTags(qname);
    return subTags.length <= index ? null : subTags[index];
  }

  protected abstract XmlTag restoreTag(String tagName);

  @Nullable
  public final XmlTag getXmlTag() {
    if (myXmlTag == null) {
      cacheDomElement(restoreTag(myTagName));
    }
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

  protected final void createFixedChildrenTags(String tagName, int count) throws IncorrectOperationException {
    checkInitialized();
    final XmlTag tag = ensureTagExists();
    final int existing = tag.findSubTags(tagName).length;
    for (int i = existing; i < count; i++) {
      tag.add(createEmptyTag(tagName));
    }
    for (Map.Entry<Method, Pair<String, Integer>> entry : myMethodsMap.getFixedChildrenEntries()) {
      getFixedChild(entry.getKey()).getXmlTag();
    }
  }

  public final DomElement addChild(final String tagName, final Class aClass, int index) throws IncorrectOperationException {
    createFixedChildrenTags(tagName, myMethodsMap.getFixedChildrenCount(tagName));
    final XmlTag tag = ensureTagExists();
    final XmlTag[] subTags = tag.findSubTags(tagName);
    if (subTags.length < index) {
      index = subTags.length;
    }
    XmlTag newTag = createEmptyTag(tagName);
    if (index == 0) {
      newTag = (XmlTag)tag.add(newTag);
    } else {
      newTag = (XmlTag)tag.addAfter(newTag, subTags[index - 1]);
    }
    return createCollectionElement(aClass, newTag);
  }
}
