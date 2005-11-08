/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DomRootInvocationHandler extends DomInvocationHandler {
  private DomFileElementImpl myParent;

  public DomRootInvocationHandler(final Class aClass,
                                  final XmlTag tag,
                                  final DomFileElementImpl fileElement,
                                  @NotNull final String tagName
  ) {
    super(aClass, tag, null, tagName, fileElement.getManager());
    myParent = fileElement;
  }

  public DomFileElementImpl getRoot() {
    return isValid() ? myParent : null;
  }

  public DomElement getParent() {
    return isValid() ? myParent : null;
  }

  protected XmlTag setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    return ((XmlDocument)getFile().getDocument().replace(((XmlFile)tag.getContainingFile()).getDocument())).getRootTag();
  }

}
