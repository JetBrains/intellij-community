/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.ClassChooserManager;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomUtil;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public class IndexedElementInvocationHandler extends DomInvocationHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.IndexedElementInvocationHandler");
  private final int myIndex;
  private final boolean myIndicator;

  public IndexedElementInvocationHandler(final Type aClass,
                                         final XmlTag tag,
                                         final DomInvocationHandler parent,
                                         final String tagName,
                                         final int index,
                                         final Converter genericConverter,
                                         final boolean indicator) {
    super(aClass, tag, parent, tagName, parent.getManager(), genericConverter);
    myIndex = index;
    myIndicator = indicator;
  }

  boolean isIndicator() {
    return myIndicator;
  }

  public final int getIndex() {
    return myIndex;
  }

  protected XmlTag setXmlTag(final XmlTag tag) throws IncorrectOperationException, IllegalAccessException, InstantiationException {
    final DomInvocationHandler parent = getParentHandler();
    parent.createFixedChildrenTags(getXmlElementName(), myIndex);
    final XmlTag newTag = (XmlTag)parent.getXmlTag().add(tag);
    if (getParentHandler().getFixedChildrenClass(tag.getName()) != null) {
      final Type type = getParentHandler().getGenericInfo().getFixedChildDescription(getXmlElementName()).getType();
      ClassChooserManager.getClassChooser(type).distinguishTag(newTag, DomUtil.getRawType(getDomElementType()));
    }
    return newTag;
  }

  public void undefineInternal() {
    final DomInvocationHandler parent = getParentHandler();
    final XmlTag parentTag = parent.getXmlTag();
    if (parentTag == null) return;

    parent.checkInitialized();
    final XmlTag[] subTags = parentTag.findSubTags(getXmlElementName());
    if (subTags.length <= myIndex) {
      return;
    }

    final boolean changing = getManager().setChanging(true);
    try {
      XmlTag tag = getXmlTag();
      assert tag != null;
      detach(false);
      if (subTags.length == myIndex + 1) {
        tag.delete();
      } else {
        attach((XmlTag) tag.replace(createEmptyTag()));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    } finally {
      getManager().setChanging(changing);
    }
    undefineChildren();
    fireUndefinedEvent();
  }

}
