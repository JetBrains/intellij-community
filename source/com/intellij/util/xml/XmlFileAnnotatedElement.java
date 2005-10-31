/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.PsiLock;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class XmlFileAnnotatedElement<T extends XmlAnnotatedElement> implements XmlAnnotatedElement {
  private final XmlFile myFile;
  private final Class<T> myRootElementClass;
  private final String myRootTagName;

  protected XmlFileAnnotatedElement(final XmlFile file,
                                    final Class<T> rootElementClass,
                                    final String rootTagName) {
    myFile = file;
    myRootElementClass = rootElementClass;
    myRootTagName = rootTagName;
  }

  public XmlFile getFile() {
    return myFile;
  }

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

  @Nullable
  public T getRootElement() {
    synchronized (PsiLock.LOCK) {
      final XmlDocument document = myFile.getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null && (myRootTagName == null || myRootTagName.equals(tag.getName()))) {
          final T element = (T) XmlAnnotatedElementManagerImpl.getCachedElement(tag);
          return element == null ? XmlAnnotatedElementManagerImpl.createXmlAnnotatedElement(myRootElementClass, tag, this, myRootTagName) : element;
        }
      }
      return null;
    }
  }

  public XmlTag getXmlTag() {
    return null;
  }

  @NotNull
  public XmlFileAnnotatedElement getRoot() {
    return this;
  }

  public XmlAnnotatedElement getParent() {
    return null;
  }
}
