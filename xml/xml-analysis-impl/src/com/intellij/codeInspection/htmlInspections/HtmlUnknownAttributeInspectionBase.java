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
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.text.EditDistance;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class HtmlUnknownAttributeInspectionBase extends HtmlUnknownElementInspection {
  private static final Key<HtmlUnknownElementInspection> ATTRIBUTE_KEY = Key.create(ATTRIBUTE_SHORT_NAME);
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.htmlInspections.HtmlUnknownAttributeInspection");

  public HtmlUnknownAttributeInspectionBase() {
    this("");
  }

  public HtmlUnknownAttributeInspectionBase(String defaultValues) {
    super(defaultValues);
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspections.unknown.attribute");
  }

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
    return ATTRIBUTE_SHORT_NAME;
  }

  @Override
  protected String getCheckboxTitle() {
    return XmlBundle.message("html.inspections.unknown.tag.attribute.checkbox.title");
  }

  @NotNull
  @Override
  protected String getPanelTitle() {
    return XmlBundle.message("html.inspections.unknown.tag.attribute.title");
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

      XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor(attribute);

      if (attributeDescriptor == null && !attribute.isNamespaceDeclaration()) {
        final String name = attribute.getName();
        if (!XmlUtil.attributeFromTemplateFramework(name, tag) && (!isCustomValuesEnabled() || !isCustomValue(name))) {
          boolean maySwitchToHtml5 = HtmlUtil.isCustomHtml5Attribute(name) && !HtmlUtil.hasNonHtml5Doctype(tag);
          ArrayList<LocalQuickFix> quickfixes = new ArrayList<>(6);
          quickfixes
            .add(new AddCustomHtmlElementIntentionAction(ATTRIBUTE_KEY, name, XmlBundle.message("add.custom.html.attribute", name)));
          quickfixes.add(new RemoveAttributeIntentionFix(name));
          if (maySwitchToHtml5) {
            quickfixes.add(new SwitchToHtml5WithHighPriorityAction());
          }
          addSimilarAttributesQuickFixes(tag, name, quickfixes);

          registerProblemOnAttributeName(attribute, XmlErrorMessages.message("attribute.is.not.allowed.here", attribute.getName()), holder,
                                         quickfixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }
      }
    }
  }

  private static void addSimilarAttributesQuickFixes(XmlTag tag, String name, ArrayList<LocalQuickFix> quickfixes) {
    XmlElementDescriptor descriptor = tag.getDescriptor();
    if (descriptor == null) return;
    XmlAttributeDescriptor[] descriptors = descriptor.getAttributesDescriptors(tag);
    int initialSize = quickfixes.size();
    for (XmlAttributeDescriptor attr : descriptors) {
      if (EditDistance.optimalAlignment(name, attr.getName(), false) <= 1) {
        quickfixes.add(new RenameAttributeFix(attr));
      }
      if (quickfixes.size() >= initialSize + 3) break;
    }
  }

  private static class RenameAttributeFix implements LocalQuickFix, HighPriorityAction {
    private final String name;

    public RenameAttributeFix(XmlAttributeDescriptor attr) {
      name = attr.getName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Rename attribute";
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Rename attribute to " + name;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      XmlAttribute attribute = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), XmlAttribute.class);
      if (attribute == null) return;
      attribute.setName(name);
    }
  }
}
