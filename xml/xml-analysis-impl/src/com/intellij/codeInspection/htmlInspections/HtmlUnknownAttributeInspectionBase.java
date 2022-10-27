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

import com.intellij.codeInsight.daemon.impl.analysis.RemoveAttributeIntentionFix;
import com.intellij.codeInspection.LocalQuickFix;
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
  @NonNls
  @NotNull
  public String getShortName() {
    return ATTRIBUTE_SHORT_NAME;
  }

  @Override
  protected String getCheckboxTitle() {
    return XmlAnalysisBundle.message("html.inspections.unknown.tag.attribute.checkbox.title");
  }

  @NotNull
  @Override
  protected String getPanelTitle() {
    return XmlAnalysisBundle.message("html.inspections.unknown.tag.attribute.title");
  }

  @Override
  @NotNull
  protected Logger getLogger() {
    return LOG;
  }

  @Override
  protected void checkAttribute(@NotNull final XmlAttribute attribute, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    final XmlTag tag = attribute.getParent();

    if (tag instanceof HtmlTag) {
      XmlElementDescriptor elementDescriptor = tag.getDescriptor();
      if (elementDescriptor == null || elementDescriptor instanceof AnyXmlElementDescriptor) {
        return;
      }

      XmlAttributeDescriptor attributeDescriptor = attribute.getDescriptor();
      if (attributeDescriptor == null && !attribute.isNamespaceDeclaration()) {
        final String name = attribute.getName();
        if (!XmlUtil.attributeFromTemplateFramework(name, tag) && (!isCustomValuesEnabled() || !isCustomValue(name))) {
          boolean maySwitchToHtml5 = HtmlUtil.isCustomHtml5Attribute(name) && !HtmlUtil.hasNonHtml5Doctype(tag);
          ArrayList<LocalQuickFix> quickfixes = new ArrayList<>(6);
          quickfixes
            .add(new AddCustomHtmlElementIntentionAction(ATTRIBUTE_KEY, name, XmlAnalysisBundle.message(
              "html.quickfix.add.custom.html.attribute", name)));
          quickfixes.add(new RemoveAttributeIntentionFix(name));
          if (maySwitchToHtml5) {
            quickfixes.add(new SwitchToHtml5WithHighPriorityAction());
          }
          addSimilarAttributesQuickFixes(tag, name, quickfixes);
          addRenameXmlAttributeQuickFixes(tag, name, quickfixes);

          registerProblemOnAttributeName(attribute, XmlAnalysisBundle.message("xml.inspections.attribute.is.not.allowed.here", attribute.getName()), holder,
                                         quickfixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }
      } else if (attributeDescriptor instanceof XmlAttributeDescriptorEx) {
        ((XmlAttributeDescriptorEx)attributeDescriptor).validateAttributeName(attribute, holder, isOnTheFly);
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

  private static void addRenameXmlAttributeQuickFixes(XmlTag tag, String name, ArrayList<? super LocalQuickFix> quickfixes) {
    for (XmlAttributeRenameProvider renameProvider : XmlAttributeRenameProvider.EP_NAME.getExtensionList()) {
      quickfixes.addAll(renameProvider.getAttributeFixes(tag, name));
    }
  }
}
