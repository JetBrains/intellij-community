/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiLock;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomMethodsInfo;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.HashMap;

/**
 * @author peter
 */
public class DomFileElementImpl<T extends DomElement> implements DomFileElement<T> {
  private final XmlFile myFile;
  private final Class<T> myRootElementClass;
  private final String myRootTagName;
  private final DomManagerImpl myManager;
  private DomInvocationHandler myRootHandler;
  private Map<Key,Object> myUserData = new HashMap<Key, Object>();

  protected DomFileElementImpl(final XmlFile file,
                               final Class<T> rootElementClass,
                               final String rootTagName,
                               final DomManagerImpl manager) {
    myFile = file;
    myRootElementClass = rootElementClass;
    myRootTagName = rootTagName;
    myManager = manager;
  }

  public XmlFile getFile() {
    return myFile;
  }

  @Nullable
  public XmlTag getRootTag() {
    final XmlDocument document = myFile.getDocument();
    if (document != null) {
      final XmlTag tag = document.getRootTag();
      if (tag != null && myRootTagName.equals(tag.getName())) {
        return tag;
      }
    }
    return null;
  }

  public final DomManagerImpl getManager() {
    return myManager;
  }

  public final Type getDomElementType() {
    return getClass();
  }

  @NotNull
  public T getRootElement() {
    return (T)getRootHandler().getProxy();
  }

  protected final DomInvocationHandler getRootHandler() {
    synchronized (PsiLock.LOCK) {
      if (myRootHandler == null) {
        final XmlTag tag = getRootTag();
        myRootHandler = new DomRootInvocationHandler(myRootElementClass, tag, this, myRootTagName);
        myManager.createDomElement(myRootHandler);
      }
      return myRootHandler;
    }
  }

  public String toString() {
    return "File " + myFile.toString();
  }

  public XmlTag getXmlTag() {
    return null;
  }

  @NotNull
  public DomFileElementImpl getRoot() {
    return this;
  }

  public DomElement getParent() {
    return null;
  }

  public XmlTag ensureTagExists() {
    return null;
  }

  public void undefine() {
  }

  public boolean isValid() {
    return myFile.isValid();
  }

  public final DomMethodsInfo getMethodsInfo() {
    return null;
  }

  public String getTagName() {
    return null;
  }

  public void acceptChildren(DomElementVisitor visitor) {
    visitor.visitDomElement(getRootElement());
  }

  public int getChildIndex(final DomElement child) {
    return -1;
  }

  public <T> T getUserData(Key<T> key) {
    return (T)myUserData.get(key);
  }

  public <T> void putUserData(Key<T> key, T value) {
    myUserData.put(key, value);
  }
}
