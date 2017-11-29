// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.xml.util;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlInspectionGroupNames;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlExtension;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim Mossienko
 */
public class CheckTagEmptyBodyInspection extends XmlSuppressableInspectionTool {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override public void visitXmlTag(final XmlTag tag) {
        if (!CheckEmptyTagInspection.isTagWithEmptyEndNotAllowed(tag)) {
          final ASTNode child = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());

          if (child != null) {
            final ASTNode node = child.getTreeNext();

            if (node != null &&
                node.getElementType() == XmlTokenType.XML_END_TAG_START) {
              final LocalQuickFix localQuickFix = new Fix();
              holder.registerProblem(
                tag,
                XmlBundle.message("xml.inspections.tag.empty.body"),
                isCollapsibleTag(tag) ? localQuickFix : null
              );
            }
          }
        }
      }
    };
  }

  static boolean isCollapsibleTag(final XmlTag tag) {
    final String name = tag.getName().toLowerCase();
    return tag.getLanguage() == XMLLanguage.INSTANCE ||
           "link".equals(name) || "br".equals(name) || "meta".equals(name) || "img".equals(name) || "input".equals(name) || "hr".equals(name) ||
           XmlExtension.isCollapsible(tag);
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return XmlInspectionGroupNames.XML_INSPECTIONS;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("xml.inspections.check.tag.empty.body");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "CheckTagEmptyBody";
  }

  @SuppressWarnings("IntentionDescriptionNotFoundInspection")
  public static class Fix extends CollapseTagIntention {
    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }
  }
}