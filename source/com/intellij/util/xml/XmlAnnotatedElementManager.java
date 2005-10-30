/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.openapi.application.ApplicationManager;

/**
 * @author peter
 */
public abstract class XmlAnnotatedElementManager {

  public static XmlAnnotatedElementManager getXmlAnnotatedElementManager() {
    return ApplicationManager.getApplication().getComponent(XmlAnnotatedElementManager.class);
  }

  public abstract <T extends XmlAnnotatedElement> T getXmlAnnotatedElement(final Class<T> aClass, final XmlTag tag);

  public abstract void setNameStrategy(final XmlFile file, final NameStrategy strategy);

  public abstract NameStrategy getNameStrategy(final XmlFile file);

  public abstract <T extends XmlAnnotatedElement> XmlFileAnnotatedElement<T> getFileElement(final XmlFile file, final Class<T> aClass);

}
