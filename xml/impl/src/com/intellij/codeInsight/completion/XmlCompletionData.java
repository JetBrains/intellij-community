/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.LeftNeighbour;
import com.intellij.psi.filters.position.XmlTokenTypeFilter;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 05.06.2003
 * Time: 18:55:15
 * To change this template use Options | File Templates.
 */
public class XmlCompletionData extends CompletionData {
  public XmlCompletionData() {
    declareFinalScope(XmlTag.class);
    declareFinalScope(XmlAttribute.class);
    declareFinalScope(XmlAttributeValue.class);

    {
      final CompletionVariant variant = new CompletionVariant(createTagCompletionFilter());
      variant.includeScopeClass(XmlTag.class);
      variant.addCompletionFilter(TrueFilter.INSTANCE);
      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(createAttributeCompletionFilter());
      variant.includeScopeClass(XmlAttribute.class);
      variant.addCompletionFilter(TrueFilter.INSTANCE);
      registerVariant(variant);
    }

    final ElementFilter entityCompletionFilter = createXmlEntityCompletionFilter();
    
    {
      final CompletionVariant variant = new CompletionVariant(
        new AndFilter(new XmlTokenTypeFilter(XmlTokenType.XML_DATA_CHARACTERS), new NotFilter(entityCompletionFilter)));
      variant.includeScopeClass(XmlToken.class, true);
      registerVariant(variant);
    }
  }

  protected ElementFilter createXmlEntityCompletionFilter() {
    return new AndFilter(new LeftNeighbour(new XmlTextFilter("&")), new OrFilter(new XmlTokenTypeFilter(XmlTokenType.XML_DATA_CHARACTERS),
                                                                                 new XmlTokenTypeFilter(
                                                                                     XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN)));
  }

  protected ElementFilter createAttributeCompletionFilter() {
    return TrueFilter.INSTANCE;
  }

  protected ElementFilter createTagCompletionFilter() {
    return TrueFilter.INSTANCE;
  }

  public static XmlFile findDescriptorFile(@NotNull XmlTag tag, @NotNull XmlFile containingFile) {
    final XmlElementDescriptor descriptor = tag.getDescriptor();
    final XmlNSDescriptor nsDescriptor = descriptor != null ? descriptor.getNSDescriptor() : null;
    XmlFile descriptorFile = nsDescriptor != null
                             ? nsDescriptor.getDescriptorFile()
                             : containingFile.getDocument().getProlog().getDoctype() != null ? containingFile : null;
    if (nsDescriptor != null && (descriptorFile == null || descriptorFile.getName().equals(containingFile.getName() + ".dtd"))) {
      descriptorFile = containingFile;
    }
    return descriptorFile;
  }
}
