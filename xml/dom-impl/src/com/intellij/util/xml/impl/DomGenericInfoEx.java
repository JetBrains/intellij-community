// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.CustomDomChildrenDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DomGenericInfoEx implements DomGenericInfo {

  public abstract boolean checkInitialized();

  @Override
  public abstract @NotNull List<AttributeChildDescriptionImpl> getAttributeChildrenDescriptions();

  final @Nullable AbstractDomChildrenDescription findChildrenDescription(DomInvocationHandler handler, XmlTag tag) {
    for (final AbstractDomChildrenDescription description : getChildrenDescriptions()) {
      if (description instanceof DomChildDescriptionImpl && !(description instanceof AttributeChildDescriptionImpl)) {
        final XmlName xmlName = ((DomChildDescriptionImpl)description).getXmlName();
        if (DomImplUtil.isNameSuitable(xmlName, tag, handler, handler.getFile())) {
          return description;
        }
      }
    }

    List<? extends CustomDomChildrenDescription> list = getCustomNameChildrenDescription();
    for (CustomDomChildrenDescription description : list) {
      if (description.getTagNameDescriptor() != null) {
        return description;
      }
    }
    return null;
  }

  public abstract boolean processAttributeChildrenDescriptions(Processor<? super AttributeChildDescriptionImpl> processor);
}
