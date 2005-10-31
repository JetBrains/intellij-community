/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.PsiLock;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class XmlFileAnnotatedElement<T extends XmlAnnotatedElement> implements XmlAnnotatedElement {
  private final XmlFile myFile;
  private final Class<T> myRootElementClass;
  private final XmlAnnotatedElementManagerImpl myManager;
  private final String myRootTagName;

  protected XmlFileAnnotatedElement(final XmlFile file, final XmlAnnotatedElementManagerImpl manager, final Class<T> rootElementClass) {
    this(file, manager, rootElementClass, null);
  }

  protected XmlFileAnnotatedElement(final XmlFile file,
                                 final XmlAnnotatedElementManagerImpl manager,
                                 final Class<T> rootElementClass,
                                 final String rootTagName) {
    myFile = file;
    myManager = manager;
    myRootElementClass = rootElementClass;
    myRootTagName = rootTagName;
  }

  public XmlFile getFile() {
    return myFile;
  }

  @Nullable
  public T getRootElement() {
    synchronized (PsiLock.LOCK) {
      final XmlDocument document = myFile.getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null && (myRootTagName == null || myRootTagName.equals(tag.getName()))) {
          final T element = (T) myManager.getCachedElement(tag);
          return element == null ? myManager.createXmlAnnotatedElement(myRootElementClass, tag) : element;
        }
      }
      return null;
    }
  }


}
