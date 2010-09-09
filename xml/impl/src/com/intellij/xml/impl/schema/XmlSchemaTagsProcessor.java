/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlSchemaTagsProcessor {

  private final Set<XmlTag> myVisited = new HashSet<XmlTag>();
  private final XmlNSDescriptorImpl myNsDescriptor;

  public XmlSchemaTagsProcessor(XmlNSDescriptorImpl nsDescriptor) {
    myNsDescriptor = nsDescriptor;
  }

  public void processTag(XmlTag tag) {

    if (myVisited.contains(tag)) return;
    myVisited.add(tag);
    doProcessTag(tag);
  }

  protected void doProcessTag(XmlTag tag) {

    if (!XmlNSDescriptorImpl.checkSchemaNamespace(tag)) {
      processTagWithSubTags(tag, true);
      return;
    }

    String tagName = tag.getLocalName();
    if (checkTagName(tagName, "element", "attribute")) {
      XmlAttribute ref = tag.getAttribute("ref");
      if (ref != null) {
        processTagWithSubTags(resolveReference(ref), false);
      }
      else {
        processTagWithSubTags(tag, false);
      }
    }
    else if (checkTagName(tagName, "group")) {
      String value = tag.getAttributeValue("ref");
      if (value != null) {
        XmlTag group = myNsDescriptor.findGroup(value);
        if (group == null) group = resolveReference(tag.getAttribute("ref"));
        processTagWithSubTags(group, true);
      }
    }
    else if (checkTagName(tagName, "attributeGroup")) {
      String ref = tag.getAttributeValue("ref");
      if (ref == null) return;
      XmlTag group = null;
      XmlTag parentTag = tag.getParentTag();
      if (XmlNSDescriptorImpl.equalsToSchemaName(parentTag, "attributeGroup") &&
        ref.equals(parentTag.getAttributeValue("name"))) {
        group = resolveReference(tag.getAttribute("ref"));
      }
      if (group == null) group = myNsDescriptor.findAttributeGroup(ref);
      processTagWithSubTags(group, true);
    }
    else if (checkTagName(tagName, "restriction", "extension")) {
      processTagWithSubTags(resolveReference(tag.getAttribute("base")), true);
      processTagWithSubTags(tag, true);
    }
    else {
      processTagWithSubTags(tag, true);
    }
  }

  private void processTagWithSubTags(@Nullable XmlTag tag, boolean processSubTags) {
    if (tag == null) return;
    tagStarted(tag, tag.getLocalName());
    if (processSubTags) {
      XmlTag[] subTags = tag.getSubTags();
      for (XmlTag subTag : subTags) {
        processTag(subTag);
      }
    }
    tagFinished(tag);
  }

  protected abstract void tagStarted(XmlTag tag, String tagName);

  protected void tagFinished(XmlTag tag) {}

  @Nullable
  private static XmlTag resolveReference(XmlAttribute ref) {
    if (ref != null) {
      XmlAttributeValue value = ref.getValueElement();
      if (value != null) {
        PsiElement element = value.getReferences()[0].resolve();
        if (element instanceof XmlTag) {
          return (XmlTag)element;
        }
      }
    }
    return null;
  }

  protected static boolean checkTagName(String tagName, String... names) {
    return ArrayUtil.contains(tagName, names);
  }
}
