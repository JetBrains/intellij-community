/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.diagnostic.Logger;

/**
 * @author peter
 */
public class IndexedElementInvocationHandler<T extends DomElement> extends DomInvocationHandler<T>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.IndexedElementInvocationHandler");
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

  public void undefine() {
    final DomInvocationHandler parent = getParentHandler();
    parent.checkInitialized();
    final XmlTag[] subTags = parent.getXmlTag().findSubTags(getTagName());
    if (subTags.length <= myIndex) {
      return;
    }

    try {
      XmlTag tag = getXmlTag();
      assert tag != null;
      if (subTags.length == myIndex + 1) {
        myXmlTag = null;
        tag.delete();
      } else {
        cacheDomElement((XmlTag) tag.replace(createEmptyTag()));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    fireUndefinedEvent();
  }

  protected XmlTag restoreTag(final String tagName) {
    return findSubTag(getParent().getXmlTag(), tagName, myIndex);
  }

}
