/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xml.impl.schema;

import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface XsdNsDescriptor extends XmlNSDescriptor, XmlNSTypeDescriptorProvider {
  boolean processTagsInNamespace(String[] tagNames, PsiElementProcessor<XmlTag> processor);

  @Nullable
  XmlElementDescriptor getElementDescriptor(String localName, String namespace, Set<XmlNSDescriptorImpl> visited, boolean reference);

  @Nullable
  XmlAttributeDescriptor getAttribute(String localName, String namespace, XmlTag context);

  @Nullable
  TypeDescriptor findTypeDescriptor(String localName, String namespace);

  @Nullable
  XmlTag findGroup(String name);

  @Nullable
  XmlTag findAttributeGroup(String name);
}
