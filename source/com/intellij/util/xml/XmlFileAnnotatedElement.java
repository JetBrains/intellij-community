/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlDocument;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class XmlFileAnnotatedElement<T extends XmlAnnotatedElement> implements XmlAnnotatedElement {
  private final XmlFile myFile;
  private final Class<T> myRootElementClass;

  public XmlFileAnnotatedElement(@NotNull final XmlFile file, final Class<T> rootElementClass) {
    myFile = file;
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
        return XmlAnnotatedElementManager.getXmlAnnotatedElement(myRootElementClass, tag);
      }
    }
    return null;
  }


}
