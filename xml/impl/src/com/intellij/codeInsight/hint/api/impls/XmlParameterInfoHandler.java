// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint.api.impls;

import com.intellij.lang.html.HtmlCompatibleFile;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
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
import com.intellij.xml.XmlBundle;
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

  public static XmlAttributeDescriptor[] getSortedDescriptors(final XmlElementDescriptor p) {
    final XmlAttributeDescriptor[] xmlAttributeDescriptors = p.getAttributesDescriptors(null);
    Arrays.sort(xmlAttributeDescriptors, COMPARATOR);
    return xmlAttributeDescriptors;
  }

  @Override
  public XmlTag findElementForParameterInfo(final @NotNull CreateParameterInfoContext context) {
    final XmlTag tag = findXmlTag(context.getFile(), context.getOffset());
    final XmlElementDescriptor descriptor = tag != null ? tag.getDescriptor() : null;

    if (descriptor == null) {
      return null;
    }

    context.setItemsToShow(new Object[] {descriptor});
    return tag;
  }

  @Override
  public void showParameterInfo(final @NotNull XmlTag element, final @NotNull CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset() + 1, this);
  }

  @Override
  public XmlTag findElementForUpdatingParameterInfo(final @NotNull UpdateParameterInfoContext context) {
    final XmlTag tag = findXmlTag(context.getFile(), context.getOffset());
    if (tag != null) {
      final PsiElement currentXmlTag = context.getParameterOwner();
      if (currentXmlTag == null || currentXmlTag == tag) return tag;
    }

    return null;
  }

  @Override
  public void updateParameterInfo(final @NotNull XmlTag parameterOwner, final @NotNull UpdateParameterInfoContext context) {
    context.setParameterOwner(parameterOwner);
  }

  private static @Nullable XmlTag findXmlTag(PsiFile file, int offset){
    if (!(file instanceof XmlFile) || file instanceof HtmlCompatibleFile) return null;

    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    element = element.getParent();

    while (element != null) {
      if (element instanceof XmlTag tag) {

        final PsiElement[] children = tag.getChildren();

        if (offset <= children[0].getTextRange().getStartOffset()) return null;

        for (PsiElement child : children) {
          final TextRange range = child.getTextRange();
          if (range.getStartOffset() <= offset && range.getEndOffset() > offset) return tag;

          if (child instanceof XmlToken token) {
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
  public void updateUI(XmlElementDescriptor o, final @NotNull ParameterInfoUIContext context) {
    XmlTag parameterOwner  = (XmlTag)context.getParameterOwner();
    updateElementDescriptor(o, context, s -> parameterOwner != null && parameterOwner.getAttributeValue(s) != null);
  }

  public static void updateElementDescriptor(XmlElementDescriptor descriptor, ParameterInfoUIContext context,
                                             Function<? super String, Boolean> attributePresentFun) {
    final XmlAttributeDescriptor[] attributes = descriptor != null ? getSortedDescriptors(descriptor) : XmlAttributeDescriptor.EMPTY;

    StringBuilder buffer = new StringBuilder();
    int highlightStartOffset = -1;
    int highlightEndOffset = -1;

    if (attributes.length == 0) {
      buffer.append(XmlBundle.message("xml.tag.info.no.attributes"));
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
