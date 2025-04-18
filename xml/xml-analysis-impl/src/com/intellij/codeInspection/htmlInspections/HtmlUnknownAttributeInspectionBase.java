// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.impl.analysis.RemoveAttributeIntentionFix;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.text.EditDistance;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.impl.XmlAttributeDescriptorEx;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class HtmlUnknownAttributeInspectionBase extends HtmlUnknownElementInspection {
  private static final Key<HtmlUnknownElementInspection> ATTRIBUTE_KEY = Key.create(ATTRIBUTE_SHORT_NAME);
  private static final Logger LOG = Logger.getInstance(HtmlUnknownAttributeInspectionBase.class);

  public HtmlUnknownAttributeInspectionBase() {
    this("");
  }

  public HtmlUnknownAttributeInspectionBase(String defaultValues) {
    super(defaultValues);
  }

  @Override
  public @NonNls @NotNull String getShortName() {
    return ATTRIBUTE_SHORT_NAME;
  }

  @Override
  protected @NotNull Logger getLogger() {
    return LOG;
  }

  @Override
  protected void checkAttribute(final @NotNull XmlAttribute attribute, final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    final XmlTag tag = attribute.getParent();

    if (tag instanceof HtmlTag) {
      XmlElementDescriptor elementDescriptor = tag.getDescriptor();
      if (elementDescriptor == null || elementDescriptor instanceof AnyXmlElementDescriptor) {
        return;
      }
      ArrayList<LocalQuickFix> quickfixes = new ArrayList<>(6);
      final String name = attribute.getName();
      boolean isFixRequired = false;
      XmlAttributeDescriptor attributeDescriptor = attribute.getDescriptor();
      if (attributeDescriptor == null && !attribute.isNamespaceDeclaration()) {
        if (!XmlUtil.attributeFromTemplateFramework(name, tag) && (!isCustomValuesEnabled() || !isCustomValue(name))) {
          isFixRequired = true;
          boolean maySwitchToHtml5 = HtmlUtil.isCustomHtml5Attribute(name) && !HtmlUtil.hasNonHtml5Doctype(tag);
          quickfixes.add(new AddCustomHtmlElementIntentionAction(ATTRIBUTE_KEY, name, XmlAnalysisBundle.message("html.quickfix.add.custom.html.attribute", name)));
          quickfixes.add(new RemoveAttributeIntentionFix(name));
          if (maySwitchToHtml5) {
            quickfixes.add(new SwitchToHtml5WithHighPriorityAction());
          }
          addSimilarAttributesQuickFixes(tag, name, quickfixes);
        }
      }
      else if (attributeDescriptor instanceof XmlAttributeDescriptorEx) {
        ((XmlAttributeDescriptorEx)attributeDescriptor).validateAttributeName(attribute, holder, isOnTheFly);
      }

      var highlightType = addUnknownXmlAttributeQuickFixes(tag, name, quickfixes, holder, isFixRequired);

      if (!quickfixes.isEmpty()) {
        registerProblemOnAttributeName(
          attribute,
          XmlAnalysisBundle.message("xml.inspections.attribute.is.not.allowed.here", name),
          holder,
          highlightType,
          quickfixes.toArray(LocalQuickFix.EMPTY_ARRAY)
        );
      }
    }
  }

  private static void addSimilarAttributesQuickFixes(XmlTag tag, String name, ArrayList<? super LocalQuickFix> quickfixes) {
    XmlElementDescriptor descriptor = tag.getDescriptor();
    if (descriptor == null) return;
    XmlAttributeDescriptor[] descriptors = descriptor.getAttributesDescriptors(tag);
    int initialSize = quickfixes.size();
    for (XmlAttributeDescriptor attr : descriptors) {
      if (EditDistance.optimalAlignment(name, attr.getName(), false, 1) <= 1) {
        quickfixes.add(new XmlAttributeRenameFix(attr));
      }
      if (quickfixes.size() >= initialSize + 3) break;
    }
  }

  private static @NotNull ProblemHighlightType addUnknownXmlAttributeQuickFixes(XmlTag tag,
                                                                       String name,
                                                                       ArrayList<? super LocalQuickFix> quickfixes,
                                                                       ProblemsHolder holder,
                                                                       boolean isFixRequired) {
    var highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    for (XmlUnknownAttributeQuickFixProvider fixProvider : XmlUnknownAttributeQuickFixProvider.EP_NAME.getExtensionList()) {
      quickfixes.addAll(fixProvider.getOrRegisterAttributeFixes(tag, name, holder, isFixRequired));
      var providerHighlightType = fixProvider.getProblemHighlightType(tag);
      if (highlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING
          && providerHighlightType != ProblemHighlightType.GENERIC_ERROR_OR_WARNING) {
        highlightType = providerHighlightType;
      }
    }
    if (XmlHighlightVisitor.isInjectedWithoutValidation(tag)
        && ProblemHighlightType.WEAK_WARNING.ordinal() < highlightType.ordinal()) {
      highlightType = ProblemHighlightType.WEAK_WARNING;
    }
    return highlightType;
  }
}
