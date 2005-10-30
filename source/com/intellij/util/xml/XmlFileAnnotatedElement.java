/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class XmlFileAnnotatedElement<T extends XmlAnnotatedElement> implements XmlAnnotatedElement {
  private final XmlFile myFile;
  private final Class<T> myRootElementClass;
  private final XmlAnnotatedElementManager myManager;

  public XmlFileAnnotatedElement(final XmlFile file, final XmlAnnotatedElementManager manager, final Class<T> rootElementClass) {
    myFile = file;
    myManager = manager;
    myRootElementClass = rootElementClass;
  }

  public XmlFile getFile() {
    return myFile;
  }

  @Nullable
  public T getRootElement() {
    final XmlDocument document = myFile.getDocument();
    if (document != null) {
      final XmlTag tag = document.getRootTag();
      if (tag != null) {
        return myManager.getXmlAnnotatedElement(myRootElementClass, tag);
      }
    }
    return null;
  }


}
