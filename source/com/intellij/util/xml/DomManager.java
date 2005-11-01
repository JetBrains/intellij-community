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
public abstract class DomManager {

  public static DomManager getXmlAnnotatedElementManager() {
    return ApplicationManager.getApplication().getComponent(DomManager.class);
  }

  public abstract void setNameStrategy(final XmlFile file, final NameStrategy strategy);

  @NotNull
  public abstract NameStrategy getNameStrategy(final XmlFile file);

  @NotNull
  public abstract <T extends DomElement> DomFileElement<T> getFileElement(XmlFile file, Class<T> aClass, String rootTagName);

  public abstract void addDomEventListener(DomEventListener listener);

  public abstract void removeDomEventListener(DomEventListener listener);
}
