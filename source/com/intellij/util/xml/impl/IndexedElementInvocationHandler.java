/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public class IndexedElementInvocationHandler extends DomInvocationHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.IndexedElementInvocationHandler");
  private final int myIndex;

  public IndexedElementInvocationHandler(final Type aClass,
                                         final XmlTag tag,
                                         final DomInvocationHandler parent,
                                         final String tagName,
                                         final int index) {
    super(aClass, tag, parent, tagName, parent.getManager());
    myIndex = index;
  }

  public final int getIndex() {
    return myIndex;
  }

  protected XmlTag setXmlTag(final XmlTag tag) throws IncorrectOperationException, IllegalAccessException, InstantiationException {
    final DomInvocationHandler parent = getParentHandler();
    parent.createFixedChildrenTags(getTagName(), myIndex);
    return (XmlTag)parent.getXmlTag().add(tag);
  }

  public void undefine() throws IllegalAccessException, InstantiationException {
    final DomInvocationHandler parent = getParentHandler();
    final XmlTag parentTag = parent.getXmlTag();
    if (parentTag == null) return;

    parent.checkInitialized();
    final XmlTag[] subTags = parentTag.findSubTags(getTagName());
    if (subTags.length <= myIndex) {
      return;
    }

    final boolean changing = getManager().setChanging(true);
    try {
      XmlTag tag = getXmlTag();
      assert tag != null;
      if (subTags.length == myIndex + 1) {
        cacheDomElement(null);
        tag.delete();
      } else {
        cacheDomElement((XmlTag) tag.replace(createEmptyTag()));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    } finally {
      getManager().setChanging(changing);
    }
    fireUndefinedEvent();
  }

}
