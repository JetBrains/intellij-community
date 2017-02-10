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
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlQuickFixFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.html.dtd.HtmlElementDescriptorImpl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class HtmlUnknownTagInspectionBase extends HtmlUnknownElementInspection {
  public static final Key<HtmlUnknownElementInspection> TAG_KEY = Key.create(TAG_SHORT_NAME);
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.htmlInspections.HtmlUnknownTagInspection");

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
  @Nls
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspections.unknown.tag");
  }

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
    return TAG_SHORT_NAME;
  }

  @Override
  @NotNull
  protected Logger getLogger() {
    return LOG;
  }

  @Override
  protected String getCheckboxTitle() {
    return XmlBundle.message("html.inspections.unknown.tag.checkbox.title");
  }

  @Override
  @NotNull
  protected String getPanelTitle() {
    return XmlBundle.message("html.inspections.unknown.tag.title");
  }

  @Override
  protected void checkTag(@NotNull final XmlTag tag, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    if (!(tag instanceof HtmlTag) || !XmlHighlightVisitor.shouldBeValidated(tag)) {
      return;
    }

    XmlElementDescriptor descriptorFromContext = XmlUtil.getDescriptorFromContext(tag);

    PsiElement parent = tag.getParent();
    XmlElementDescriptor parentDescriptor = parent instanceof XmlTag ? ((XmlTag)parent).getDescriptor() : null;

    XmlElementDescriptor ownDescriptor = isAbstractDescriptor(descriptorFromContext)
                                         ? tag.getDescriptor()
                                         : descriptorFromContext;

    if (isAbstractDescriptor(ownDescriptor) ||
        (parentDescriptor instanceof HtmlElementDescriptorImpl &&
         ownDescriptor instanceof HtmlElementDescriptorImpl &&
         isAbstractDescriptor(descriptorFromContext))) {

      final String name = tag.getName();

      if (!isCustomValuesEnabled() || !isCustomValue(name)) {
        final AddCustomHtmlElementIntentionAction action = new AddCustomHtmlElementIntentionAction(TAG_KEY, name, XmlBundle.message("add.custom.html.tag", name));

        // todo: support "element is not allowed" message for html5
        // some tags in html5 cannot be found in xhtml5.xsd if they are located in incorrect context, so they get any-element descriptor (ex. "canvas: tag)
        final String message = isAbstractDescriptor(ownDescriptor)
                               ? XmlErrorMessages.message("unknown.html.tag", name)
                               : XmlErrorMessages.message("element.is.not.allowed.here", name);

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
        ProblemHighlightType highlightType = tag.getContainingFile().getContext() == null ?
                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING :
                                             ProblemHighlightType.INFORMATION;
        if (startTagName.getTextLength() > 0) {
          holder.registerProblem(startTagName, message, highlightType, quickfixes.toArray(new LocalQuickFix[quickfixes.size()]));
        }

        if (endTagName != null) {
          holder.registerProblem(endTagName, message, highlightType, quickfixes.toArray(new LocalQuickFix[quickfixes.size()]));
        }
      }
    }
  }

  @Nullable
  protected LocalQuickFix createChangeTemplateDataFix(PsiFile file) {
    return null;
  }
}
