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
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class XmlSchemaTagsProcessor {

  private final Set<XmlTag> myVisited = new HashSet<XmlTag>();
  private final XmlNSDescriptorImpl myNsDescriptor;

  public XmlSchemaTagsProcessor(XmlNSDescriptorImpl nsDescriptor) {
    myNsDescriptor = nsDescriptor;
  }

  public boolean processTags(XmlTag tag, Processor<XmlTag> processor) {

    if (myVisited.contains(tag)) return true;
    myVisited.add(tag);
    if (checkTagName(tag, "element")) {
      if (tag.getAttribute("name") != null) {
        return processor.process(tag);
      }
      else {
        XmlTag referenced = resolveReference(tag.getAttribute("ref"));
        if (referenced != null) {
          return processor.process(referenced);
        }
      }
    }
    else if (checkTagName(tag, "group")) {
      String value = tag.getAttributeValue("ref");
      if (value != null) {
        XmlTag group = myNsDescriptor.findGroup(value);
        return processTagWithSubTags(group, processor);
      }
    }
    else if (checkTagName(tag, "restriction", "extension")) {
      return processTagWithSubTags(resolveReference(tag.getAttribute("base")), processor) &&
             processTagWithSubTags(tag, processor);
    }
    else {
      return processTagWithSubTags(tag, processor);
    }
    return true;
  }

  private boolean processTagWithSubTags(@Nullable XmlTag tag, Processor<XmlTag> processor) {
    if (tag == null) return true;
    if (!processor.process(tag)) {
      return false;
    }
    XmlTag[] subTags = tag.getSubTags();
    for (XmlTag subTag : subTags) {
      if (!processTags(subTag, processor)) {
        return false;
      }
    }
    return true;
  }

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

  protected static boolean checkTagName(XmlTag tag, String... names) {
    return ArrayUtil.contains(tag.getLocalName(), names) && XmlNSDescriptorImpl.checkSchemaNamespace(tag);
  }
}
