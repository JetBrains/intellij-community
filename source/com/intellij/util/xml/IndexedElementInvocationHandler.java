/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

/**
 * @author peter
 */
public class IndexedElementInvocationHandler<T extends DomElement> extends DomInvocationHandler<T>{
  private final int myIndex;

  public IndexedElementInvocationHandler(final Class<T> aClass,
                                         final XmlTag tag,
                                         final DomInvocationHandler parent,
                                         final String tagName,
                                         final int index) {
    super(aClass, tag, parent, tagName, parent.getManager());
    myIndex = index;
  }

  protected void setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    final DomInvocationHandler parent = getParentHandler();
    parent.createFixedChildrenTags(getTagName(), myIndex);
    parent.getXmlTag().add(tag);
  }

  protected XmlTag restoreTag(final String tagName) {
    return findSubTag(getParent().getXmlTag(), tagName, myIndex);
  }

}
