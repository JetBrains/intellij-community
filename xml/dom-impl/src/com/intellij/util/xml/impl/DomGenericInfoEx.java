/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  @NotNull
  public abstract List<AttributeChildDescriptionImpl> getAttributeChildrenDescriptions();

  @Nullable
  final AbstractDomChildrenDescription findChildrenDescription(DomInvocationHandler handler, XmlTag tag) {
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
