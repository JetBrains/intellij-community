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
  private DomFileElement myParent;

  public DomRootInvocationHandler(final Class<T> aClass,
                                  final XmlTag tag,
                                  final DomFileElement<T> fileElement,
                                  @NotNull final String tagName
  ) {
    super(aClass, tag, null, tagName, fileElement.getManager());
    myParent = fileElement;
  }

  public DomFileElement getRoot() {
    return isValid() ? myParent : null;
  }

  public DomElement getParent() {
    return isValid() ? myParent : null;
  }

  protected void setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    getFile().getDocument().replace(((XmlFile)tag.getContainingFile()).getDocument());
  }

  protected XmlTag restoreTag(final String tagName) {
    return ((DomFileElement)getParent()).getRootTag();
  }
}
