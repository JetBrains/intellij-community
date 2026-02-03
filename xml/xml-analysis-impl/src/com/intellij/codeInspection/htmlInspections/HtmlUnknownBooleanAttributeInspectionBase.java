// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.impl.analysis.RemoveAttributeIntentionFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlQuickFixFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import com.intellij.xml.impl.schema.AnyXmlAttributeDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.psi.XmlPsiBundle;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class HtmlUnknownBooleanAttributeInspectionBase extends HtmlUnknownElementInspection {
  private static final Key<HtmlUnknownElementInspection> BOOLEAN_ATTRIBUTE_KEY = Key.create(BOOLEAN_ATTRIBUTE_SHORT_NAME);
  private static final Logger LOG = Logger.getInstance(HtmlUnknownBooleanAttributeInspectionBase.class);

  public HtmlUnknownBooleanAttributeInspectionBase() {
    this("");
  }

  public HtmlUnknownBooleanAttributeInspectionBase(String defaultValues) {
    super(defaultValues);
  }

  @Override
  public @NonNls @NotNull String getShortName() {
    return BOOLEAN_ATTRIBUTE_SHORT_NAME;
  }

  @Override
  protected @NotNull Logger getLogger() {
    return LOG;
  }

  @Override
  protected void checkAttribute(final @NotNull XmlAttribute attribute, final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    if (attribute.getValueElement() == null) {
      final XmlTag tag = attribute.getParent();

      if (tag instanceof HtmlTag) {
        XmlElementDescriptor elementDescriptor = tag.getDescriptor();
        if (elementDescriptor == null || elementDescriptor instanceof AnyXmlElementDescriptor) {
          return;
        }

        XmlAttributeDescriptor attributeDescriptor = attribute.getDescriptor();
        if (attributeDescriptor != null && !(attributeDescriptor instanceof AnyXmlAttributeDescriptor)) {
          String name = attribute.getName();
          if (!HtmlUtil.isBooleanAttribute(attributeDescriptor, null) && (!isCustomValuesEnabled() || !isCustomValue(name))) {
            final boolean html5 = HtmlUtil.isHtml5Context(tag);
            LocalQuickFix[] quickFixes = !html5 ? new LocalQuickFix[]{
              new AddCustomHtmlElementIntentionAction(BOOLEAN_ATTRIBUTE_KEY, name, XmlAnalysisBundle.message(
                "html.quickfix.add.custom.html.boolean.attribute", name)),
              XmlQuickFixFactory.getInstance().addAttributeValueFix(attribute),
              new RemoveAttributeIntentionFix(name),
            } : new LocalQuickFix[] {
              XmlQuickFixFactory.getInstance().addAttributeValueFix(attribute)
            };


            String error = null;
            if (html5) {
              if (attributeDescriptor instanceof XmlEnumerationDescriptor
                  && ((XmlEnumerationDescriptor<?>)attributeDescriptor).getValueDeclaration(attribute, "") == null) {
                error = XmlPsiBundle.message("xml.inspections.attribute.requires.value", attribute.getName());
              }
            } else {
              error = XmlAnalysisBundle.message("html.inspections.attribute.is.not.boolean", attribute.getName());
            }
            if (error != null) {
              registerProblemOnAttributeName(attribute, error, holder, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, quickFixes);
            }
          }
        }
      }
    }
  }
}
