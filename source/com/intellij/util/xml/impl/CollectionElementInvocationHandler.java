/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.events.CollectionElementRemovedEvent;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class CollectionElementInvocationHandler<T extends DomElement> extends DomInvocationHandler<T>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.CollectionElementInvocationHandler");

  public CollectionElementInvocationHandler(final Class<T> aClass,
                                            @NotNull final XmlTag tag,
                                            final DomInvocationHandler parent) {
    super(aClass, tag, parent, tag.getName(), parent.getManager());
  }

  protected final void setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    getParent().ensureTagExists().add(tag);
  }

  public final void undefine() {
    final DomElement parent = getParent();
    invalidate();
    deleteTag(getXmlTag());
    getManager().fireEvent(new CollectionElementRemovedEvent(getProxy(), parent, getTagName()));
  }

  protected final XmlTag restoreTag(String tagName) {
    final XmlTag tag = getParent().getXmlTag();
    return tag == null ? null : tag.findFirstSubTag(getTagName());
  }

}
