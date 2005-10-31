/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class XmlAnnotatedElementManager {

  public static XmlAnnotatedElementManager getXmlAnnotatedElementManager() {
    return ApplicationManager.getApplication().getComponent(XmlAnnotatedElementManager.class);
  }

  public abstract void setNameStrategy(final XmlFile file, final NameStrategy strategy);

  @NotNull
  public abstract NameStrategy getNameStrategy(final XmlFile file);

  @NotNull
  public abstract <T extends XmlAnnotatedElement> XmlFileAnnotatedElement<T> getFileElement(final XmlFile file, final Class<T> aClass);

  @NotNull
  public abstract <T extends XmlAnnotatedElement> XmlFileAnnotatedElement<T> getFileElement(XmlFile file, Class<T> aClass, String rootTagName);
}
