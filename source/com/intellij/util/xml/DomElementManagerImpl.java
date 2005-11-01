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

/**
 * @author peter
 */
public class DomElementManagerImpl extends DomElementManager implements ApplicationComponent {
  private static final Key<NameStrategy> NAME_STRATEGY_KEY = Key.create("NameStrategy");
  private static final Key<DomElement> CACHED_ELEMENT = Key.create("CachedXmlAnnotatedElement");

  @NotNull
  protected static <T extends DomElement>T createXmlAnnotatedElement(final Class<T> aClass,
                                                                     final XmlTag tag,
                                                                     final DomElement parent,
                                                                     final String tagName) {
    synchronized (PsiLock.LOCK) {
      final DomInvocationHandler<T> handler = new DomInvocationHandler<T>(aClass, tag, parent, tagName);
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
        element = new DomFileElement<T>(file, aClass, rootTagName);
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
