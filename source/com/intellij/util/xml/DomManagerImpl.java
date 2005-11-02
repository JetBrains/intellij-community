/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiLock;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import net.sf.cglib.proxy.Proxy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

/**
 * @author peter
 */
public class DomManagerImpl extends DomManager implements ApplicationComponent {
  private static final Key<NameStrategy> NAME_STRATEGY_KEY = Key.create("NameStrategy");
  private static final Key<DomElement> CACHED_ELEMENT = Key.create("CachedXmlAnnotatedElement");

  private final List<DomEventListener> myListeners = new ArrayList<DomEventListener>();
  private DomEventListener[] myCachedListeners;
  private ConverterManager myConverterManager = new ConverterManager();

  public void addDomEventListener(DomEventListener listener) {
    myCachedListeners = null;
    myListeners.add(listener);
  }

  public void removeDomEventListener(DomEventListener listener) {
    myCachedListeners = null;
    myListeners.remove(listener);
  }

  public ConverterManager getConverterManager() {
    return myConverterManager;
  }

  protected void fireEvent(DomEvent event) {
    DomEventListener[] listeners = myCachedListeners;
    if (listeners == null) {
      listeners = myCachedListeners = myListeners.toArray(new DomEventListener[myListeners.size()]);
    }
    for (DomEventListener listener : listeners) {
      listener.eventOccured(event);
    }
  }

  @NotNull
  protected <T extends DomElement>T createDomElement(final Class<T> aClass,
                                                     final XmlTag tag,
                                                     final DomElement parent,
                                                     final String tagName) {
    return createDomElement(aClass, tag, new DomInvocationHandler<T>(aClass, tag, parent, tagName, this));
  }

  protected static <T extends DomElement>T createDomElement(final Class<T> aClass,
                                                            final XmlTag tag,
                                                            final DomInvocationHandler<T> handler) {
    synchronized (PsiLock.LOCK) {
      final T element = newProxyInstance(aClass, handler);
      handler.setProxy(element);
      setCachedElement(tag, element);
      return element;
    }
  }

  private static <T extends DomElement>T newProxyInstance(final Class<T> aClass,
                                                          final DomInvocationHandler<T> invocationHandler) {
    return (T)Proxy.newProxyInstance(null, new Class[]{aClass}, invocationHandler);
  }

  public final void setNameStrategy(final XmlFile file, final NameStrategy strategy) {
    file.putUserData(NAME_STRATEGY_KEY, strategy);
  }

  @NotNull
  public final NameStrategy getNameStrategy(final XmlFile file) {
    return _getNameStrategy(file);
  }

  protected static NameStrategy _getNameStrategy(final XmlFile file) {
    final NameStrategy strategy = file.getUserData(NAME_STRATEGY_KEY);
    return strategy == null ? NameStrategy.HYPHEN_STRATEGY : strategy;
  }

  @NotNull
  public <T extends DomElement> DomFileElement<T> getFileElement(final XmlFile file,
                                                                 final Class<T> aClass,
                                                                 String rootTagName) {
    synchronized (PsiLock.LOCK) {
      DomFileElement<T> element = (DomFileElement<T>)getCachedElement(file);
      if (element == null) {
        element = new DomFileElement<T>(file, aClass, rootTagName, this);
        setCachedElement(file, element);
      }
      return element;
    }
  }

  protected static void setCachedElement(final XmlElement xmlElement, final DomElement element) {
    if (xmlElement != null) {
      xmlElement.putUserData(CACHED_ELEMENT, element);
    }
  }

  @Nullable
  public static DomElement getCachedElement(final XmlElement element) {
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





}
