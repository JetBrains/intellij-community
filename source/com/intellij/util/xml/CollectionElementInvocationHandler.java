/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

/**
 * @author peter
 */
public class CollectionElementInvocationHandler<T extends DomElement> extends DomInvocationHandler<T>{
  public CollectionElementInvocationHandler(final Class<T> aClass,
                                            final XmlTag tag,
                                            final DomInvocationHandler parent,
                                            final String tagName
  ) {
    super(aClass, tag, parent, tagName, parent.getManager());
  }

  protected void setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    getParent().ensureTagExists().add(tag);
  }

  protected XmlTag restoreTag(String tagName) {
    final XmlTag tag = getParent().getXmlTag();
    return tag == null ? null : tag.findFirstSubTag(getTagName());
  }

}
