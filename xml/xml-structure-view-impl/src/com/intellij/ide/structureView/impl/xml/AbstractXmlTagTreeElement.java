// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.xml;

import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.structureView.xml.XmlStructureViewElementProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;

public abstract class AbstractXmlTagTreeElement<T extends XmlElement> extends PsiTreeElementBase<T> {
  static {
    XmlStructureViewElementProvider.EP_NAME.addChangeListener(() -> ApplicationManager
      .getApplication().getMessageBus().syncPublisher(StructureViewWrapperImpl.STRUCTURE_CHANGED).run(), null);
  }
  protected AbstractXmlTagTreeElement(final T psiElement) {
    super(psiElement);
  }

  protected static Collection<StructureViewTreeElement> getStructureViewTreeElements(XmlTag[] subTags) {
    return ContainerUtil.map2List(subTags, xmlTag -> {
      for (final XmlStructureViewElementProvider provider : XmlStructureViewElementProvider.EP_NAME.getExtensionList()) {
        final StructureViewTreeElement element = provider.createCustomXmlTagTreeElement(xmlTag);
        if (element != null) {
          return element;
        }
      }
      return new XmlTagTreeElement(xmlTag);
    });
  }
}
