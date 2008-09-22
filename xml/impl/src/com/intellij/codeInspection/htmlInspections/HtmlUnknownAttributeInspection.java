/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
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
    super("type,wmode,src,width,height");
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

      if (attributeDescriptor == null) {
        if (!XmlUtil.attributeFromTemplateFramework(name, tag) && (!isCustomValuesEnabled() || !isCustomValue(name))) {
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
}
