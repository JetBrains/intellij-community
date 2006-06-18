/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.xml.util;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.resolve.reference.ElementManipulator;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.xml.XmlBundle;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.lang.ASTNode;

import org.jetbrains.annotations.NonNls;

/**
 * @author Maxim Mossienko
 */
public class CheckEmptyScriptTagInspection extends LocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.util.CheckEmptyScriptTagInspection");
  @NonNls private static final String SCRIPT_TAG_NAME = "script";

  public boolean isEnabledByDefault() {
    return true;
  }

  public PsiElementVisitor buildVisitor(final ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {}

      public void visitXmlTag(final XmlTag tag) {
        if (SCRIPT_TAG_NAME.equals(tag.getName()) ||
            (tag instanceof HtmlTag && SCRIPT_TAG_NAME.equalsIgnoreCase(tag.getLocalName()))
           ) {
          final ASTNode child = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(tag.getNode());

          if (child != null) {

            final LocalQuickFix fix = new LocalQuickFix() {
              public String getName() {
                return XmlBundle.message("html.inspections.check.empty.script.tag.fix.message");
              }

              public void applyFix(Project project, ProblemDescriptor descriptor) {
                final StringBuilder builder = new StringBuilder(tag.getText());
                builder.replace(builder.length() - 2, builder.length() - 1, "></" + SCRIPT_TAG_NAME + ">");

                try {
                  final XmlTag tagFromText = tag.getManager().getElementFactory().createTagFromText(builder.toString());
                  tag.replace(tagFromText);
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }

              //to appear in "Apply Fix" statement when multiple Quick Fixes exist
              public String getFamilyName() {
                return getName();
              }
            };

            holder.registerProblem(tag,
                                   XmlBundle.message("html.inspections.check.empty.script.message"),
                                   fix);
          }
        }
      }
    };
  }

  public String getGroupDisplayName() {
    return GroupNames.HTML_INSPECTIONS;
  }

  public String getDisplayName() {
    return XmlBundle.message("html.inspections.check.empty.script.tag");
  }

  @NonNls
  public String getShortName() {
    return "CheckEmptyScriptTag";
  }
}
