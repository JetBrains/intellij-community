// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlQuickFixFactory;
import com.intellij.polySymbols.html.elements.HtmlElementSymbolDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.html.dtd.HtmlElementDescriptorImpl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.impl.XmlElementDescriptorEx;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.xml.util.XmlUtil.isNotInjectedOrCustomHtmlFile;

public class HtmlUnknownTagInspectionBase extends HtmlUnknownElementInspection {
  public static final Key<HtmlUnknownElementInspection> TAG_KEY = Key.create(TAG_SHORT_NAME);
  private static final Logger LOG = Logger.getInstance(HtmlUnknownTagInspectionBase.class);

  public HtmlUnknownTagInspectionBase(@NotNull String defaultValues) {
    super(defaultValues);
  }

  public HtmlUnknownTagInspectionBase() {
    this("nobr,noembed,comment,noscript,embed,script");
  }

  private static boolean isAbstractDescriptor(XmlElementDescriptor descriptor) {
    return descriptor == null || descriptor instanceof AnyXmlElementDescriptor;
  }

  @Override
  public @NonNls @NotNull String getShortName() {
    return TAG_SHORT_NAME;
  }

  @Override
  protected @NotNull Logger getLogger() {
    return LOG;
  }

  @Override
  protected void checkTag(final @NotNull XmlTag tag, final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    if (!(tag instanceof HtmlTag) || !XmlHighlightVisitor.shouldBeValidated(tag)) {
      return;
    }

    XmlElementDescriptor descriptorFromContext = XmlUtil.getDescriptorFromContext(tag);

    PsiElement parent = tag.getParent();
    XmlElementDescriptor parentDescriptor = parent instanceof XmlTag ? ((XmlTag)parent).getDescriptor() : null;

    XmlElementDescriptor ownDescriptor = isAbstractDescriptor(descriptorFromContext)
                                         ? tag.getDescriptor()
                                         : descriptorFromContext;

    if (ownDescriptor instanceof XmlElementDescriptorEx) {
      ((XmlElementDescriptorEx)ownDescriptor).validateTagName(tag, holder, isOnTheFly);
      return;
    }
    if (descriptorFromContext instanceof XmlElementDescriptorEx) {
      ((XmlElementDescriptorEx)descriptorFromContext).validateTagName(tag, holder, isOnTheFly);
      return;
    }

    if (isAbstractDescriptor(ownDescriptor) ||
        ((parentDescriptor instanceof HtmlElementDescriptorImpl
          || parentDescriptor instanceof HtmlElementSymbolDescriptor htmlElementSymbolDescriptor
             && !htmlElementSymbolDescriptor.isCustomElement()) &&
         ownDescriptor instanceof HtmlElementDescriptorImpl &&
         isAbstractDescriptor(descriptorFromContext))) {

      final String name = tag.getName();

      if (!isCustomValuesEnabled() || !isCustomValue(name)) {
        final AddCustomHtmlElementIntentionAction action = new AddCustomHtmlElementIntentionAction(TAG_KEY, name, XmlAnalysisBundle.message(
          "html.quickfix.add.custom.html.tag", name));

        // todo: support "element is not allowed" message for html5
        // some tags in html5 cannot be found in xhtml5.xsd if they are located in incorrect context, so they get any-element descriptor (ex. "canvas: tag)
        final String message = isAbstractDescriptor(ownDescriptor)
                               ? XmlAnalysisBundle.message("xml.inspections.unknown.html.tag", name)
                               : XmlAnalysisBundle.message("xml.inspections.element.is.not.allowed.here", name);

        final PsiElement startTagName = XmlTagUtil.getStartTagNameElement(tag);
        assert startTagName != null;
        final PsiElement endTagName = XmlTagUtil.getEndTagNameElement(tag);

        List<LocalQuickFix> quickfixes = new ArrayList<>();
        quickfixes.add(action);
        if (isOnTheFly) {
          PsiFile file = startTagName.getContainingFile();
          if (file instanceof XmlFile) {
            quickfixes.add(XmlQuickFixFactory.getInstance().createNSDeclarationIntentionFix(startTagName, "", null));
          }

          // People using non-HTML as their template data language (but having not changed this in the IDE)
          // will most likely see 'unknown html tag' error, because HTML is usually the default.
          // So if they check quick fixes for this error they'll discover Change Template Data Language feature.
          ContainerUtil.addIfNotNull(quickfixes, createChangeTemplateDataFix(file));
        }
        if (HtmlUtil.isHtml5Tag(name) && !HtmlUtil.hasNonHtml5Doctype(tag)) {
          quickfixes.add(new SwitchToHtml5WithHighPriorityAction());
        }
        ProblemHighlightType highlightType = isNotInjectedOrCustomHtmlFile(tag.getContainingFile())
                                             ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                                             : ProblemHighlightType.INFORMATION;
        if (isOnTheFly || highlightType != ProblemHighlightType.INFORMATION) {
          if (startTagName.getTextLength() > 0) {
            holder.registerProblem(startTagName, message, highlightType, quickfixes.toArray(LocalQuickFix.EMPTY_ARRAY));
          }

          if (endTagName != null) {
            holder.registerProblem(endTagName, message, highlightType, quickfixes.toArray(LocalQuickFix.EMPTY_ARRAY));
          }
        }
      }
    }
  }

  protected @Nullable LocalQuickFix createChangeTemplateDataFix(PsiFile file) {
    return null;
  }
}
