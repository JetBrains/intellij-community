/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;

import java.util.Arrays;
import java.util.Collections;

/**
 * @author peter
 */
public class GetVariableChildrenInvocation implements Invocation {
  private final String myQname;
  private final int myStartIndex;

  public GetVariableChildrenInvocation(final String qname, final int startIndex) {
    myQname = qname;
    myStartIndex = startIndex;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    XmlTag tag = handler.getXmlTag();
    if (tag == null) return Collections.emptyList();

    handler.checkInitialized();
    final XmlTag[] subTags = tag.findSubTags(myQname);
    DomElement[] elements = new DomElement[subTags.length - myStartIndex];
    for (int i = myStartIndex; i < subTags.length; i++) {
      final DomElement element = DomManagerImpl.getCachedElement(subTags[i]);
      assert element != null : "Null annotated element for " + tag.getText() + "; " + myQname + "; " + i;
      elements[i - myStartIndex] = element;
    }
    return Arrays.asList(elements);
  }
}
