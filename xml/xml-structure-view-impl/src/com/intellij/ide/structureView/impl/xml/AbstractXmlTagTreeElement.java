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
package com.intellij.ide.structureView.impl.xml;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.xml.XmlStructureViewElementProvider;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;

public abstract class AbstractXmlTagTreeElement<T extends XmlElement> extends PsiTreeElementBase<T> {
  protected AbstractXmlTagTreeElement(final T psiElement) {
    super(psiElement);
  }

  protected static Collection<StructureViewTreeElement> getStructureViewTreeElements(XmlTag[] subTags) {
    final XmlStructureViewElementProvider[] providers =
      (XmlStructureViewElementProvider[])Extensions.getExtensions(XmlStructureViewElementProvider.EXTENSION_POINT_NAME);

    return ContainerUtil.map2List(subTags, new Function<XmlTag, StructureViewTreeElement>() {
      @Override
      public StructureViewTreeElement fun(final XmlTag xmlTag) {
        for (final XmlStructureViewElementProvider provider : providers) {
          final StructureViewTreeElement element = provider.createCustomXmlTagTreeElement(xmlTag);
          if (element != null) {
            return element;
          }
        }
        return new XmlTagTreeElement(xmlTag);
      }
    });
  }
}
