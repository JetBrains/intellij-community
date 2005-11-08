/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.events.CollectionElementRemovedEvent;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class CollectionElementInvocationHandler extends DomInvocationHandler{

  public CollectionElementInvocationHandler(final Class aClass,
                                            @NotNull final XmlTag tag,
                                            final DomInvocationHandler parent) {
    super(aClass, tag, parent, tag.getName(), parent.getManager());
  }

  protected final XmlTag setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    return (XmlTag) getParent().ensureTagExists().add(tag);
  }

  public final void undefine() {
    final DomElement parent = getParent();
    invalidate();
    deleteTag(getXmlTag());
    getManager().fireEvent(new CollectionElementRemovedEvent(getProxy(), parent, getTagName()));
  }

}
