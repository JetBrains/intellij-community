/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.hint.api.impls;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.MutableLookupElement;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.Function;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;

/**
 * @author Maxim.Mossienko
 */
public class XmlParameterInfoHandler implements ParameterInfoHandler<XmlTag,XmlElementDescriptor> {
  private static final Comparator<XmlAttributeDescriptor> COMPARATOR = Comparator.comparing(PsiMetaData::getName);

  @Override
  public Object[] getParametersForLookup(LookupElement item, ParameterInfoContext context) {
    if (!(item instanceof MutableLookupElement)) return null;
    final Object lookupItem = item.getObject();
    if (lookupItem instanceof XmlElementDescriptor) return new Object[]{lookupItem};
    return null;
  }

  public static XmlAttributeDescriptor[] getSortedDescriptors(final XmlElementDescriptor p) {
    final XmlAttributeDescriptor[] xmlAttributeDescriptors = p.getAttributesDescriptors(null);
    Arrays.sort(xmlAttributeDescriptors, COMPARATOR);
    return xmlAttributeDescriptors;
  }

  @Override
  public boolean couldShowInLookup() {
    return true;
  }

  @Override
  public XmlTag findElementForParameterInfo(@NotNull final CreateParameterInfoContext context) {
    final XmlTag tag = findXmlTag(context.getFile(), context.getOffset());
    final XmlElementDescriptor descriptor = tag != null ? tag.getDescriptor() : null;

    if (descriptor == null) {
      DaemonCodeAnalyzer.getInstance(context.getProject()).updateVisibleHighlighters(context.getEditor());
      return null;
    }

    context.setItemsToShow(new Object[] {descriptor});
    return tag;
  }

  @Override
  public void showParameterInfo(final @NotNull XmlTag element, @NotNull final CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset() + 1, this);
  }

  @Override
  public XmlTag findElementForUpdatingParameterInfo(@NotNull final UpdateParameterInfoContext context) {
    final XmlTag tag = findXmlTag(context.getFile(), context.getOffset());
    if (tag != null) {
      final PsiElement currentXmlTag = context.getParameterOwner();
      if (currentXmlTag == null || currentXmlTag == tag) return tag;
    }

    return null;
  }

  @Override
  public void updateParameterInfo(@NotNull final XmlTag parameterOwner, @NotNull final UpdateParameterInfoContext context) {
    if (context.getParameterOwner() == null || parameterOwner.equals(context.getParameterOwner())) {
      context.setParameterOwner(parameterOwner);
    } else {
      context.removeHint();
    }
  }

  @Nullable
  private static XmlTag findXmlTag(PsiFile file, int offset){
    if (!(file instanceof XmlFile)) return null;

    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    element = element.getParent();

    while (element != null) {
      if (element instanceof XmlTag) {
        XmlTag tag = (XmlTag)element;

        final PsiElement[] children = tag.getChildren();

        if (offset <= children[0].getTextRange().getStartOffset()) return null;

        for (PsiElement child : children) {
          final TextRange range = child.getTextRange();
          if (range.getStartOffset() <= offset && range.getEndOffset() > offset) return tag;

          if (child instanceof XmlToken) {
            XmlToken token = (XmlToken)child;
            if (token.getTokenType() == XmlTokenType.XML_TAG_END) return null;
          }
        }

        return null;
      }

      element = element.getParent();
    }

    return null;
  }

  @Override
  public void updateUI(XmlElementDescriptor o, @NotNull final ParameterInfoUIContext context) {
    updateElementDescriptor(
      o,
      context,
      new Function<String, Boolean>() {
        final XmlTag parameterOwner  = (XmlTag)context.getParameterOwner();

        @Override
        public Boolean fun(String s) {
          return parameterOwner != null && parameterOwner.getAttributeValue(s) != null;
        }
      });
  }

  public static void updateElementDescriptor(XmlElementDescriptor descriptor, ParameterInfoUIContext context,
                                             Function<String, Boolean> attributePresentFun) {
    final XmlAttributeDescriptor[] attributes = descriptor != null ? getSortedDescriptors(descriptor) : XmlAttributeDescriptor.EMPTY;

    StringBuilder buffer = new StringBuilder();
    int highlightStartOffset = -1;
    int highlightEndOffset = -1;

    if (attributes.length == 0) {
      buffer.append(CodeInsightBundle.message("xml.tag.info.no.attributes"));
    }
    else {
      StringBuilder text1 = new StringBuilder(" ");
      StringBuilder text2 = new StringBuilder(" ");
      StringBuilder text3 = new StringBuilder(" ");

      for (XmlAttributeDescriptor attribute : attributes) {
        if (Boolean.TRUE.equals(attributePresentFun.fun(attribute.getName()))) {
          if (!(text1.toString().equals(" "))) {
            text1.append(", ");
          }
          text1.append(attribute.getName());
        }
        else if (attribute.isRequired()) {
          if (!(text2.toString().equals(" "))) {
            text2.append(", ");
          }
          text2.append(attribute.getName());
        }
        else {
          if (!(text3.toString().equals(" "))) {
            text3.append(", ");
          }
          text3.append(attribute.getName());
        }
      }

      if (!text1.toString().equals(" ") && !text2.toString().equals(" ")) {
        text1.append(", ");
      }

      if (!text2.toString().equals(" ") && !text3.toString().equals(" ")) {
        text2.append(", ");
      }

      if (!text1.toString().equals(" ") && !text3.toString().equals(" ") && text2.toString().equals(" ")) {
        text1.append(", ");
      }

      buffer.append(text1);
      highlightStartOffset = buffer.length();
      buffer.append(text2);
      highlightEndOffset = buffer.length();
      buffer.append(text3);
    }

    context.setupUIComponentPresentation(buffer.toString(), highlightStartOffset, highlightEndOffset, false,
                                         false, true, context.getDefaultParameterColor());
  }
}
