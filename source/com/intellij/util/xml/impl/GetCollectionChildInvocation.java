/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class GetCollectionChildInvocation implements Invocation {
  private final String myQname;
  private final int myStartIndex;

  public GetCollectionChildInvocation(final String qname, final int startIndex) {
    myQname = qname;
    myStartIndex = startIndex;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    XmlTag tag = handler.getXmlTag();
    if (tag == null) return Collections.emptyList();

    handler.checkInitialized();
    final XmlTag[] subTags = tag.findSubTags(myQname);
    if (subTags.length <= myStartIndex) return Collections.emptyList();

    List<DomElement> elements = new ArrayList<DomElement>(subTags.length - myStartIndex);
    for (int i = myStartIndex; i < subTags.length; i++) {
      final DomInvocationHandler element = DomManagerImpl.getCachedElement(subTags[i]);
      if (element != null) {
        elements.add(element.getProxy());
      }
    }
    return Collections.unmodifiableList(elements);
  }
}
