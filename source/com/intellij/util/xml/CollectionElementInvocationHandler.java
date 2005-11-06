/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class CollectionElementInvocationHandler<T extends DomElement> extends DomInvocationHandler<T>{
  private boolean myInvalidated;

  public CollectionElementInvocationHandler(final Class<T> aClass,
                                            @NotNull final XmlTag tag,
                                            final DomInvocationHandler parent) {
    super(aClass, tag, parent, tag.getName(), parent.getManager());
  }

  protected void setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    getParent().ensureTagExists().add(tag);
  }

  public boolean isValid() {
    return !myInvalidated;
  }

  public void undefine() {
    myInvalidated = true;
    super.undefine();
  }

  protected XmlTag restoreTag(String tagName) {
    final XmlTag tag = getParent().getXmlTag();
    return tag == null ? null : tag.findFirstSubTag(getTagName());
  }

}
