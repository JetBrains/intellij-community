/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DomRootInvocationHandler<T extends DomElement> extends DomInvocationHandler<T>{
  public DomRootInvocationHandler(final Class<T> aClass, final XmlTag tag, final DomFileElement<T> parent, @NotNull final String tagName, DomManagerImpl manager) {
    super(aClass, tag, parent, tagName, manager);
  }

  @NotNull
  public DomFileElement getRoot() {
    return getParent();
  }

  @NotNull
  public DomFileElement<T> getParent() {
    return (DomFileElement<T>) super.getParent();
  }

  @NotNull
  protected XmlFile getFile() {
    return getParent().getFile();
  }

  protected void setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    getFile().getDocument().replace(((XmlFile)tag.getContainingFile()).getDocument());
  }

  protected XmlTag restoreTag(final String tagName) {
    return getParent().getRootTag();
  }
}
