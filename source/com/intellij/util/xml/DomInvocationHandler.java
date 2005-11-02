/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiLock;
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
class DomInvocationHandler<T extends DomElement> implements InvocationHandler, DomElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.DomInvocationHandler");
  private static final Map<Method, Invocation> ourInvocations = new HashMap<Method, Invocation>();

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
  private final MethodsMap myMethodsMap;


  static {
    for (final Method method : DomElement.class.getMethods()) {
      ourInvocations.put(method, new Invocation() {
        public Object invoke(DomInvocationHandler handler, final Method method, Object[] args) throws Throwable {
          return method.invoke(handler, args);
        }
      });
    }
    for (final Method method : Object.class.getMethods()) {
      if ("equals".equals(method.getName())) {
        ourInvocations.put(method, new Invocation() {
          public Object invoke(DomInvocationHandler handler, final Method method, Object[] args) throws Throwable {
            return handler.getProxy() == args[0];
          }
        });
      }
      else {
        ourInvocations.put(method, new Invocation() {
          public Object invoke(DomInvocationHandler handler, final Method method, Object[] args) throws Throwable {
            return method.invoke(handler, args);
          }
        });
      }
    }
  }


  public DomInvocationHandler(final Class<T> aClass,
                              final XmlTag tag,
                              final DomElement parent,
                              @NotNull final String tagName,
                              DomManagerImpl manager) {
    myClass = aClass;
    myTag = tag;
    myParent = parent;
    myTagName = tagName;
    myManager = manager;
    myMethodsMap = manager.getMethodsMap(aClass);
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
    return getConverter(method, getter).fromString(s, new ConvertContext(getFile(), getXmlTag()));
  }

  @NotNull
  private Converter getConverter(final Method method, final boolean getter) throws IllegalAccessException, InstantiationException {
    return myManager.getConverterManager().getConverter(method, getter);
  }

  String convertToString(Method method, Object argument, final boolean getter)
    throws IllegalAccessException, InstantiationException {
    if (argument == null) return null;
    return getConverter(method, getter).toString(argument, new ConvertContext(getFile(), getXmlTag()));
  }

  public final DomElement getProxy() {
    return myProxy;
  }

  public final void setProxy(final DomElement proxy) {
    myProxy = proxy;
  }

  public MethodsMap getMethodsMap() {
    return myMethodsMap;
  }

  @NotNull
  protected XmlFile getFile() {
    if (myFile == null) {
      myFile = getRoot().getFile();
    }
    return myFile;
  }

  private Invocation createInvocation(final Method method) {
    boolean getter = isGetter(method);
    boolean setter = isSetter(method);

    final TagValue tagValue = method.getAnnotation(TagValue.class);
    if (getter && (tagValue != null || "getValue".equals(method.getName()))) {
      return new Invocation.GetValueInvocation();
    }
    else if (setter && (tagValue != null || "setValue".equals(method.getName()))) {
      return new Invocation.SetValueInvocation();
    }

    myMethodsMap.buildMethodMaps(getFile());

    if (myMethodsMap.isFixedChildrenMethod(method)) {
      return new Invocation.GetFixedChildInvocation();
    }

    if (myMethodsMap.isVariableChildrenMethod(method)) {
      return new Invocation.GetVariableChildrenInvocation(myMethodsMap, method);
    }

    throw new UnsupportedOperationException("No implementation for method " + method.toString());
  }

  DomElement getFixedChild(final Method method) {
    return myMethod2Children.get(method);
  }

  public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    Invocation invocation = ourInvocations.get(method);
    if (invocation == null) {
      invocation = createInvocation(method);
      ourInvocations.put(method, invocation);
    }
    return invocation.invoke(this, method, args);
  }

  static boolean isBoolean(final Class<?> type) {
    return type.equals(boolean.class) || type.equals(Boolean.class);
  }

  void setTagValue(final XmlTag tag, final String value) {
    tag.getValue().setText(value);
  }

  Object getValue(final XmlTag tag, final Method method) throws IllegalAccessException, InstantiationException {
    return tag != null ? convertFromString(method, getTagValue(tag), true) : null;
  }

  String getTagValue(final XmlTag tag) {
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
        for (Map.Entry<Method, String> entry : myMethodsMap.getFixedChildrenEntries()) {
          Method method = entry.getKey();
          final String qname = entry.getValue();
          XmlTag subTag = tag == null ? null : tag.findFirstSubTag(qname);
          final DomElement element = myManager.createDomElement((Class<DomElement>)method.getReturnType(), subTag, getProxy(), qname);
          myMethod2Children.put(method, element);
          usedTags.add(subTag);
        }

        if (tag != null) {
          for (Map.Entry<Method, Pair<String, Class<? extends DomElement>>> entry : myMethodsMap.getVariableChildrenEntries()) {
            final Pair<String, Class<? extends DomElement>> pair = entry.getValue();
            String qname = pair.getFirst();
            for (XmlTag subTag : tag.findSubTags(qname)) {
              if (!usedTags.contains(subTag)) {
                myManager.createDomElement(pair.getSecond(), subTag, getProxy(), qname);
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

  public DomManagerImpl getManager() {
    return myManager;
  }

}
