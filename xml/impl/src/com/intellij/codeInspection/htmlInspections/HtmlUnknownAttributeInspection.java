/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class HtmlUnknownAttributeInspection extends HtmlUnknownTagInspection {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.htmlInspections.HtmlUnknownAttributeInspection");
  @NonNls public static final String ATTRIBUTE_SHORT_NAME = "HtmlUnknownAttribute";

  public HtmlUnknownAttributeInspection() {
    super("");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspections.unknown.attribute");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return ATTRIBUTE_SHORT_NAME;
  }

  protected String getCheckboxTitle() {
    return XmlBundle.message("html.inspections.unknown.tag.attribute.checkbox.title");
  }

  protected String getPanelTitle() {
    return XmlBundle.message("html.inspections.unknown.tag.attribute.title");
  }

  @NotNull
  protected Logger getLogger() {
    return LOG;
  }

  protected void checkTag(@NotNull final XmlTag tag, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    // does nothing! this method should be overriden empty!
  }

  protected void checkAttribute(@NotNull final XmlAttribute attribute, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    final XmlTag tag = attribute.getParent();

    if (tag instanceof HtmlTag) {
      XmlElementDescriptor elementDescriptor = tag.getDescriptor();
      if (elementDescriptor == null || elementDescriptor instanceof AnyXmlElementDescriptor) {
        return;
      }

      XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor(attribute);

      final String name = attribute.getName();

      if (attributeDescriptor == null && !attribute.isNamespaceDeclaration()) {
        if (!XmlUtil.attributeFromTemplateFramework(name, tag) &&
            (!isCustomValuesEnabled() || !isCustomValue(name)) &&
            !isHtml5DataAttribute(tag, name)) {
          final ASTNode node = attribute.getNode();
          assert node != null;
          final PsiElement nameElement = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(node).getPsi();

          holder.registerProblem(nameElement, XmlErrorMessages.message("attribute.is.not.allowed.here", name),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                 new AddCustomTagOrAttributeIntentionAction(getShortName(), name, XmlEntitiesInspection.UNKNOWN_ATTRIBUTE),
                                 new RemoveAttributeIntentionAction(name, attribute));
        }
      }
    }
  }

  private static boolean isHtml5DataAttribute(XmlTag tag, String attrName) {
    return attrName.startsWith(HtmlUtil.HTML5_DATA_ATTR_PREFIX) && HtmlUtil.isHtml5Context(tag);
  }

}
