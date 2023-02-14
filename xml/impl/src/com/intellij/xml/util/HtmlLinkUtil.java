// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class HtmlLinkUtil {
  @NonNls public static final String LINK = "link";

  public static void processLinks(@NotNull final XmlFile xhtmlFile,
                                  @NotNull Processor<? super XmlTag> tagProcessor) {
    final XmlDocument doc = HtmlUtil.getRealXmlDocument(xhtmlFile.getDocument());
    if (doc == null) return;

    final XmlTag rootTag = doc.getRootTag();
    if (rootTag == null) return;

    if (LINK.equalsIgnoreCase(rootTag.getName())) {
      tagProcessor.process(rootTag);
    }
    else {
      findLinkStylesheets(rootTag, tagProcessor);
    }
  }

  public static void findLinkStylesheets(@NotNull final XmlTag tag,
                                         @NotNull Processor<? super XmlTag> tagProcessor) {
    processInjectedContent(tag, tagProcessor);

    for (XmlTag subTag : tag.getSubTags()) {
      findLinkStylesheets(subTag, tagProcessor);
    }

    if (LINK.equalsIgnoreCase(tag.getName())) {
      tagProcessor.process(tag);
    }
  }

  public static void processInjectedContent(final XmlTag element,
                                            @NotNull final Processor<? super XmlTag> tagProcessor) {
    final PsiLanguageInjectionHost.InjectedPsiVisitor injectedPsiVisitor = (injectedPsi, places) -> {
      if (injectedPsi instanceof XmlFile) {
        final XmlDocument injectedDocument = ((XmlFile)injectedPsi).getDocument();
        if (injectedDocument != null) {
          final XmlTag rootTag = injectedDocument.getRootTag();
          if (rootTag != null) {
            for (PsiElement element1 = rootTag; element1 != null; element1 = element1.getNextSibling()) {
              if (element1 instanceof XmlTag tag) {
                String tagName = tag.getLocalName();
                if (element1 instanceof HtmlTag || tag.getNamespacePrefix().length() > 0) tagName = StringUtil.toLowerCase(tagName);
                if (LINK.equalsIgnoreCase(tagName)) {
                  tagProcessor.process((XmlTag)element1);
                }
              }
            }
          }
        }
      }
    };

    final XmlText[] texts = PsiTreeUtil.getChildrenOfType(element, XmlText.class);
    if (texts != null) {
      for (final XmlText text : texts) {
        for (PsiElement _element : text.getChildren()) {
          if (_element instanceof PsiLanguageInjectionHost) {
            InjectedLanguageManager.getInstance(_element.getProject()).enumerate(_element, injectedPsiVisitor);
          }
        }
      }
    }

    final XmlComment[] comments = PsiTreeUtil.getChildrenOfType(element, XmlComment.class);
    if (comments != null) {
      for (final XmlComment comment : comments) {
        if (comment instanceof PsiLanguageInjectionHost) {
          InjectedLanguageManager.getInstance(comment.getProject()).enumerate(comment, injectedPsiVisitor);
        }
      }
    }
  }
}
