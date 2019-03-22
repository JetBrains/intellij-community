// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.structureView.impl.xml;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.structureView.xml.XmlStructureViewElementProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;

public abstract class AbstractXmlTagTreeElement<T extends XmlElement> extends PsiTreeElementBase<T> {
  protected AbstractXmlTagTreeElement(final T psiElement) {
    super(psiElement);
  }

  protected static Collection<StructureViewTreeElement> getStructureViewTreeElements(XmlTag[] subTags) {
    final XmlStructureViewElementProvider[] providers =
      (XmlStructureViewElementProvider[])Extensions.getRootArea().getExtensionPoint(XmlStructureViewElementProvider.EXTENSION_POINT_NAME)
        .getExtensions();

    return ContainerUtil.map2List(subTags, xmlTag -> {
      for (final XmlStructureViewElementProvider provider : providers) {
        final StructureViewTreeElement element = provider.createCustomXmlTagTreeElement(xmlTag);
        if (element != null) {
          return element;
        }
      }
      return new XmlTagTreeElement(xmlTag);
    });
  }
}
