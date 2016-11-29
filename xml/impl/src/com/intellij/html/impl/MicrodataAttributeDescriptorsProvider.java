/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.html.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptorsProvider;
import com.intellij.xml.impl.schema.AnyXmlAttributeDescriptor;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.html.impl.util.MicrodataUtil.*;

/**
 * @author: Fedor.Korotkov
 */
public class MicrodataAttributeDescriptorsProvider implements XmlAttributeDescriptorsProvider {
  @Override
  public XmlAttributeDescriptor[] getAttributeDescriptors(XmlTag context) {
    if (!HtmlUtil.isHtml5Context(context)) {
      return XmlAttributeDescriptor.EMPTY;
    }
    final String tagName = context.getName();
    List<XmlAttributeDescriptor> result = new ArrayList<>();
    final boolean goodContextForProps =
      "div".equalsIgnoreCase(tagName) || "span".equalsIgnoreCase(tagName) || "a".equalsIgnoreCase(tagName);
    if (goodContextForProps && hasScopeTag(context)) {
      result.add(new MicrodataPropertyAttributeDescriptor(context));
    }
    if (context.getAttribute(ITEM_SCOPE) == null) {
      result.add(new AnyXmlAttributeDescriptor(ITEM_SCOPE));
    }
    else {
      result.add(new XmlAttributeDescriptorWithEmptyDefaultValue(ITEM_ID));
      result.add(new XmlAttributeDescriptorWithEmptyDefaultValue(ITEM_TYPE));
      result.add(new XmlAttributeDescriptorWithEmptyDefaultValue(ITEM_REF));
    }
    return result.toArray(new XmlAttributeDescriptor[result.size()]);
  }

  @Nullable
  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, XmlTag context) {
    if (!HtmlUtil.isHtml5Context(context)) {
      return null;
    }
    if (ITEM_SCOPE.equalsIgnoreCase(attributeName)) {
      return new AnyXmlAttributeDescriptor(attributeName);
    }
    if (context.getAttribute(ITEM_SCOPE) != null &&
        (ITEM_TYPE.equalsIgnoreCase(attributeName) || ITEM_ID.equalsIgnoreCase(attributeName) || ITEM_REF.equalsIgnoreCase(attributeName))) {
      return new XmlAttributeDescriptorWithEmptyDefaultValue(attributeName);
    }
    if (ITEM_PROP.equalsIgnoreCase(attributeName) && hasScopeTag(context)) {
      return new MicrodataPropertyAttributeDescriptor(context);
    }
    return null;
  }

  private static class XmlAttributeDescriptorWithEmptyDefaultValue extends AnyXmlAttributeDescriptor {
    public XmlAttributeDescriptorWithEmptyDefaultValue(String name) {
      super(name);
    }

    @Override
    public String getDefaultValue() {
      return "";
    }
  }

  private static class MicrodataPropertyAttributeDescriptor extends AnyXmlAttributeDescriptor {

    @NotNull
    private final XmlTag myContext;

    public MicrodataPropertyAttributeDescriptor(@NotNull XmlTag context) {
      super(ITEM_PROP);
      myContext = context;
    }

    @Override
    public String getDefaultValue() {
      return "";
    }

    @Override
    public boolean isEnumerated() {
      final String[] enumeratedValues = getEnumeratedValues();
      return enumeratedValues == null ? super.isEnumerated() : enumeratedValues.length > 0;
    }

    @Override
    public String[] getEnumeratedValues() {
      final XmlTag scopeParent = findScopeTag(myContext);
      return scopeParent != null ? findProperties(scopeParent) : super.getEnumeratedValues();
    }

    private static String[] findProperties(@NotNull XmlTag tag) {
      final XmlAttribute typeAttribute = tag.getAttribute(ITEM_TYPE);
      if (typeAttribute != null) {
        final XmlAttributeValue valueElement = typeAttribute.getValueElement();
        final PsiReference[] references = valueElement != null ? valueElement.getReferences() : PsiReference.EMPTY_ARRAY;
        List<String> result = new ArrayList<>();
        for (PsiReference reference : references) {
          final PsiElement target = reference != null ? reference.resolve() : null;
          if (target instanceof PsiFile) {
            result.addAll(extractProperties((PsiFile)target, StringUtil.stripQuotesAroundValue(reference.getCanonicalText())));
          }
        }
        return ArrayUtil.toStringArray(result);
      }
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }
}
