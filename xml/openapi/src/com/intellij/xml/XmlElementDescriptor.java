/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xml;

import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * Describes an XML tag.
 *
 * @author Mike
 */
public interface XmlElementDescriptor extends PsiMetaData {
  XmlElementDescriptor[] EMPTY_ARRAY = new XmlElementDescriptor[0];

  @NonNls
  String getQualifiedName();

  /**
   * Should return either simple or qualified name depending on the schema/DTD properties.
   * This name should be used in XML documents.
   * @return either simple or qualified name.
   */
  @NonNls
  String getDefaultName();

  /**
   * Returns an array of child tag descriptors.
   * @param context the parent tag.
   * @return an array of child tag descriptors, or empty array if no child tag allowed.
   */
  XmlElementDescriptor[] getElementsDescriptors(XmlTag context);

  @Nullable
  XmlElementDescriptor getElementDescriptor(XmlTag childTag, XmlTag contextTag);

  XmlAttributeDescriptor[] getAttributesDescriptors(final @Nullable XmlTag context);
  @Nullable
  XmlAttributeDescriptor getAttributeDescriptor(@NonNls String attributeName, final @Nullable XmlTag context);
  @Nullable
  XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute);

  XmlNSDescriptor getNSDescriptor();

  /**
   * @return minimal occurrence constraint value (e.g. 0 or 1), on null if not applied
   */
  @Nullable
  XmlElementsGroup getTopGroup();

  int getContentType();

  int CONTENT_TYPE_EMPTY = 0;
  int CONTENT_TYPE_ANY = 1;
  int CONTENT_TYPE_CHILDREN = 2;
  int CONTENT_TYPE_MIXED = 3;
}
