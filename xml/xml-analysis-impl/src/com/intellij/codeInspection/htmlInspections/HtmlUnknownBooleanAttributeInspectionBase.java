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
import com.intellij.codeInsight.daemon.impl.analysis.RemoveAttributeIntentionFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlQuickFixFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import com.intellij.xml.impl.schema.AnyXmlAttributeDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.Nls;
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
  @Nls
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspections.unknown.boolean.attribute");
  }

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
    return BOOLEAN_ATTRIBUTE_SHORT_NAME;
  }

  @Override
  protected String getCheckboxTitle() {
    return XmlBundle.message("html.inspections.unknown.tag.boolean.attribute.checkbox.title");
  }

  @NotNull
  @Override
  protected String getPanelTitle() {
    return XmlBundle.message("html.inspections.unknown.tag.boolean.attribute.title");
  }

  @Override
  @NotNull
  protected Logger getLogger() {
    return LOG;
  }

  @Override
  protected void checkAttribute(@NotNull final XmlAttribute attribute, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    if (attribute.getValueElement() == null) {
      final XmlTag tag = attribute.getParent();

      if (tag instanceof HtmlTag) {
        XmlElementDescriptor elementDescriptor = tag.getDescriptor();
        if (elementDescriptor == null || elementDescriptor instanceof AnyXmlElementDescriptor) {
          return;
        }

        XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor(attribute);
        if (attributeDescriptor != null && !(attributeDescriptor instanceof AnyXmlAttributeDescriptor)) {
          String name = attribute.getName();
          if (!HtmlUtil.isBooleanAttribute(attributeDescriptor, null) && (!isCustomValuesEnabled() || !isCustomValue(name))) {
            final boolean html5 = HtmlUtil.isHtml5Context(tag);
            LocalQuickFix[] quickFixes = !html5 ? new LocalQuickFix[]{
              new AddCustomHtmlElementIntentionAction(BOOLEAN_ATTRIBUTE_KEY, name, XmlBundle.message("add.custom.html.boolean.attribute", name)),
              XmlQuickFixFactory.getInstance().addAttributeValueFix(attribute),
              new RemoveAttributeIntentionFix(name),
            } : new LocalQuickFix[] {
              XmlQuickFixFactory.getInstance().addAttributeValueFix(attribute)
            };


            String error = null;
            if (html5) {
              if (attributeDescriptor instanceof XmlEnumerationDescriptor &&
                  ((XmlEnumerationDescriptor)attributeDescriptor).getValueDeclaration(attribute, "") == null) {
                error = XmlErrorMessages.message("wrong.value", "attribute");
              }
            } else {
              error = XmlErrorMessages.message("attribute.is.not.boolean", attribute.getName());
            }
            if (error != null) {
              registerProblemOnAttributeName(attribute, error, holder, quickFixes);
            }
          }
        }
      }
    }
  }
}
