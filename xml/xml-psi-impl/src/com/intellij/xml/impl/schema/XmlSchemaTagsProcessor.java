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
import com.intellij.psi.PsiReference;
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

  public final static ThreadLocal<Boolean> PROCESSING_FLAG = new ThreadLocal<>();

  private final Set<XmlTag> myVisited = new HashSet<>();
  protected final XmlNSDescriptorImpl myNsDescriptor;
  private final String[] myTagsToIgnore;

  public XmlSchemaTagsProcessor(XmlNSDescriptorImpl nsDescriptor, String... tagsToIgnore) {
    myNsDescriptor = nsDescriptor;
    myTagsToIgnore = ArrayUtil.append(tagsToIgnore, "annotation");
  }

  public final void startProcessing(XmlTag tag) {
    try {
      PROCESSING_FLAG.set(Boolean.TRUE);
      processTag(tag, null);
    }
    finally {
      PROCESSING_FLAG.set(null);
    }
  }

  private void processTag(XmlTag tag, @Nullable XmlTag context) {

    if (myVisited.contains(tag)) return;
    myVisited.add(tag);

    if (!XmlNSDescriptorImpl.checkSchemaNamespace(tag)) {
      processTagWithSubTags(tag, context, null);
      return;
    }

    String tagName = tag.getLocalName();
    if (checkTagName(tagName, "element", "attribute")) {
      XmlAttribute ref = tag.getAttribute("ref");
      if (ref != null) {
        XmlTag resolved = resolveTagReference(ref);
        if (resolved != null) {
          tagStarted(resolved, resolved.getLocalName(), tag, tag);
        }
      }
      else {
        tagStarted(tag, tag.getLocalName(), context, null);
      }
    }
    else if (checkTagName(tagName, "group")) {
      String value = tag.getAttributeValue("ref");
      if (value != null) {
        XmlTag group = myNsDescriptor.findGroup(value);
        if (group == null) group = resolveTagReference(tag.getAttribute("ref"));
        processTagWithSubTags(group, tag, tag);
      }
    }
    else if (checkTagName(tagName, "attributeGroup")) {
      String ref = tag.getAttributeValue("ref");
      if (ref == null) return;
      XmlTag group;
      XmlTag parentTag = tag.getParentTag();
      assert parentTag != null;
      if (XmlNSDescriptorImpl.equalsToSchemaName(parentTag, "attributeGroup") &&
        ref.equals(parentTag.getAttributeValue("name"))) {
        group = resolveTagReference(tag.getAttribute("ref"));
        if (group == null) group = myNsDescriptor.findAttributeGroup(ref);
      }
      else {
        group =  myNsDescriptor.findAttributeGroup(ref);
        if (group == null) group = resolveTagReference(tag.getAttribute("ref"));
      }
      processTagWithSubTags(group, tag, null);
    }
    else if (checkTagName(tagName, "restriction", "extension")) {
      processTagWithSubTags(resolveTagReference(tag.getAttribute("base")), tag, null);
      processTagWithSubTags(tag, context, null);
    }
    else if (!checkTagName(tagName, myTagsToIgnore)) {
      processTagWithSubTags(tag, context, null);
    }
  }

  private void processTagWithSubTags(@Nullable XmlTag tag, XmlTag ctx, @Nullable XmlTag ref) {
    if (tag == null) return;
    tagStarted(tag, tag.getLocalName(), ctx, ref);
    XmlTag[] subTags = tag.getSubTags();
    for (XmlTag subTag : subTags) {
      processTag(subTag, tag);
    }
    tagFinished(tag);
  }

  protected abstract void tagStarted(XmlTag tag, String tagName, XmlTag context, @Nullable XmlTag ref);

  protected void tagFinished(XmlTag tag) {}

  @Nullable
  private static XmlTag resolveTagReference(XmlAttribute ref) {
    PsiElement element = resolveReference(ref);
    return element instanceof XmlTag ? (XmlTag)element : null;
  }

  @Nullable
  static PsiElement resolveReference(XmlAttribute ref) {
    if (ref != null) {
      XmlAttributeValue value = ref.getValueElement();
      if (value != null) {
        PsiReference[] references = value.getReferences();
        if (references.length > 0)
          return references[0].resolve();
      }
    }
    return null;
  }

  protected static boolean checkTagName(String tagName, String... names) {
    return ArrayUtil.contains(tagName, names);
  }
}
