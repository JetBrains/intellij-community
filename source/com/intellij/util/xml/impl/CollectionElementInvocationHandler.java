/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class CollectionElementInvocationHandler<T extends DomElement> extends DomInvocationHandler<T>{

  public CollectionElementInvocationHandler(final Class<T> aClass,
                                            @NotNull final XmlTag tag,
                                            final DomInvocationHandler parent) {
    super(aClass, tag, parent, tag.getName(), parent.getManager());
  }

  protected final void setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    getParent().ensureTagExists().add(tag);
  }

  public final void undefine() {
    invalidate();
    super.undefine();
  }

  protected final XmlTag restoreTag(String tagName) {
    final XmlTag tag = getParent().getXmlTag();
    return tag == null ? null : tag.findFirstSubTag(getTagName());
  }

}
