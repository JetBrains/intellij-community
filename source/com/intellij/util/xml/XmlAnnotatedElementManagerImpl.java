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
public class XmlAnnotatedElementManagerImpl extends XmlAnnotatedElementManager implements ApplicationComponent {
  private static final Key<NameStrategy> NAME_STRATEGY_KEY = Key.create("NameStrategy");
  private static final Key<XmlAnnotatedElement> CACHED_ELEMENT = Key.create("CachedXmlAnnotatedElement");

  @NotNull
  protected static <T extends XmlAnnotatedElement>T createXmlAnnotatedElement(final Class<T> aClass,
                                                                     final XmlTag tag,
                                                                     final XmlAnnotatedElement parent,
                                                                     final String tagName) {
    synchronized (PsiLock.LOCK) {
      final XmlAnnotatedElementImpl<T> handler = new XmlAnnotatedElementImpl<T>(aClass, tag, parent, tagName);
      final T element = newProxyInstance(aClass, handler);
      handler.setProxy(element);
      setCachedElement(tag, element);
      return element;
    }
  }

  private static <T extends XmlAnnotatedElement>T newProxyInstance(final Class<T> aClass,
                                                            final XmlAnnotatedElementImpl<T> invocationHandler) {
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
  public <T extends XmlAnnotatedElement> XmlFileAnnotatedElement<T> getFileElement(final XmlFile file,
                                                                                   final Class<T> aClass,
                                                                                   String rootTagName) {
    synchronized (PsiLock.LOCK) {
      XmlFileAnnotatedElement<T> element = (XmlFileAnnotatedElement<T>)getCachedElement(file);
      if (element == null) {
        element = new XmlFileAnnotatedElement<T>(file, aClass, rootTagName);
        setCachedElement(file, element);
      }
      return element;
    }
  }

  protected static void setCachedElement(final XmlElement xmlElement, final XmlAnnotatedElement element) {
    if (xmlElement != null) {
      xmlElement.putUserData(CACHED_ELEMENT, element);
    }
  }

  @Nullable
  public static XmlAnnotatedElement getCachedElement(final XmlElement element) {
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
