/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.events.ElementDefinedEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public class AttributeChildInvocationHandler extends DomInvocationHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.AttributeChildInvocationHandler");
  private boolean myWasDefined;

  protected AttributeChildInvocationHandler(final Type type,
                                            final XmlTag tag,
                                            final DomInvocationHandler parent,
                                            final String attributeName,
                                            final DomManagerImpl manager,
                                            final Converter genericConverter) {
    super(type, tag, parent, attributeName, manager, genericConverter);
    if (tag != null && tag.getAttributeValue(attributeName) != null) {
      myWasDefined = true;
    }
  }

  protected final XmlTag setXmlTag(final XmlTag tag) {
    return tag;
  }

  protected void cacheInTag(final XmlTag tag) {
  }

  public boolean wasDefined() {
    return myWasDefined;
  }

  public void setDefined(final boolean wasDefined) {
    myWasDefined = wasDefined;
  }

  protected void removeFromCache() {
  }

  protected final Invocation createSetValueInvocation(final Converter converter) {
    return new SetAttributeValueInvocation(converter);
  }

  protected final Invocation createGetValueInvocation(final Converter converter) {
    return new GetAttributeValueInvocation(converter);
  }

  public void undefine() {
    final XmlTag tag = getXmlTag();
    if (tag != null) {
      try {
        tag.setAttribute(getXmlElementName(), null);
        setDefined(false);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      fireUndefinedEvent();
    }
  }

  @Nullable
  public final XmlTag getXmlTag() {
    return getParentHandler().getXmlTag();
  }

  public XmlTag ensureTagExists() {
    final XmlTag tag = getXmlTag();
    getParentHandler().ensureTagExists();
    if (tag == null) {
      getManager().fireEvent(new ElementDefinedEvent(getProxy()));
    }
    return getXmlTag();
  }

}
