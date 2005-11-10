/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiLock;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class DomFileElementImpl<T extends DomElement> implements DomFileElement<T> {
  private final XmlFile myFile;
  private final Class<T> myRootElementClass;
  private final String myRootTagName;
  private final DomManagerImpl myManager;
  private T myRootValue;

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

  public DomManagerImpl getManager() {
    return myManager;
  }

  @NotNull
  public T getRootElement() {
    synchronized (PsiLock.LOCK) {
      if (myRootValue == null) {
        final XmlTag tag = getRootTag();
        final DomRootInvocationHandler handler = new DomRootInvocationHandler(myRootElementClass, tag, this, myRootTagName);
        myRootValue = (T)myManager.createDomElement(myRootElementClass, tag, handler);
      }
      return myRootValue;
    }
  }

  void invalidateRoot() {
    synchronized (PsiLock.LOCK) {
      myRootValue = null;
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

}
