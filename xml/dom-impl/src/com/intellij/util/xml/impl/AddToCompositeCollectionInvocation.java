// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.EvaluatedXmlName;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

final class AddToCompositeCollectionInvocation implements Invocation {
  private final CollectionChildDescriptionImpl myMainDescription;
  private final Set<? extends CollectionChildDescriptionImpl> myQnames;
  private final Type myType;

  AddToCompositeCollectionInvocation(final CollectionChildDescriptionImpl tagName, final Set<? extends CollectionChildDescriptionImpl> qnames, final Type type) {
    myMainDescription = tagName;
    myQnames = qnames;
    myType = type;
  }

  @Override
  public Object invoke(final DomInvocationHandler handler, final Object[] args) {
    final XmlTag tag = handler.ensureTagExists();

    Set<XmlTag> set = new HashSet<>();
    for (final CollectionChildDescriptionImpl qname : myQnames) {
      set.addAll(qname.getCollectionSubTags(handler, tag, true));
    }
    int index = args != null && args.length == 1 ? (Integer)args[0] : Integer.MAX_VALUE;

    XmlTag lastTag = null;
    int i = 0;
    final XmlTag[] tags = tag.getSubTags();
    for (final XmlTag subTag : tags) {
      if (i == index) break;
      if (set.contains(subTag)) {
        lastTag = subTag;
        i++;
      }
    }
    final DomManagerImpl manager = handler.getManager();
    final boolean b = manager.setChanging(true);
    try {
      final EvaluatedXmlName evaluatedXmlName = handler.createEvaluatedXmlName(myMainDescription.getXmlName());
      final XmlTag emptyTag = handler.createChildTag(evaluatedXmlName);
      final XmlTag newTag;
      if (lastTag == null) {
        if (tags.length == 0) {
          newTag = (XmlTag)tag.add(emptyTag);
        }
        else {
          newTag = (XmlTag)tag.addBefore(emptyTag, tags[0]);
        }
      }
      else {
        newTag = (XmlTag)tag.addAfter(emptyTag, lastTag);
      }

      return new CollectionElementInvocationHandler(myType, newTag, myMainDescription, handler, null).getProxy();
    }
    finally {
      manager.setChanging(b);
    }
  }


}
