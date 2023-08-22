// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;

import java.util.*;

final class GetCompositeCollectionInvocation implements Invocation {
  private final Set<? extends CollectionChildDescriptionImpl> myQnames;

  GetCompositeCollectionInvocation(final Set<? extends CollectionChildDescriptionImpl> qnames) {
    myQnames = qnames;
  }

  @Override
  public Object invoke(final DomInvocationHandler handler, final Object[] args) {
    Map<XmlTag,DomElement> map = new HashMap<>();
    for (final CollectionChildDescriptionImpl qname : myQnames) {
      for (DomElement element : handler.getCollectionChildren(qname)) {
        map.put(element.getXmlTag(), element);
      }
    }
    final XmlTag tag = handler.getXmlTag();
    if (tag == null) return Collections.emptyList();

    final List<DomElement> list = new ArrayList<>();
    for (final XmlTag subTag : tag.getSubTags()) {
      ContainerUtil.addIfNotNull(list, map.get(subTag));
    }
    return list;
  }
}
