// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class CheckTagEmptyBodyInspection extends XmlSuppressableInspectionTool {

  public CheckTagEmptyBodyInspection() {
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (XmlHighlightVisitor.isInjectedWithoutValidation(holder.getFile())) {
      // not inside injected code
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new XmlElementVisitor() {
      @Override public void visitXmlTag(final @NotNull XmlTag tag) {
        if (tag instanceof HtmlTag) {
          return;
        }
        final ASTNode child = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());
        if (child == null) {
          return;
        }
        final ASTNode node = child.getTreeNext();

        if (node != null
            && node.getElementType() == XmlTokenType.XML_END_TAG_START) {
          holder.registerProblem(
            tag,
            XmlAnalysisBundle.message("xml.inspections.tag.empty.body"),
            LocalQuickFix.notNullElements(isCollapsibleTag(tag) ? new CollapseTagIntention() : null)
          );
        }
      }
    };
  }

  static boolean isCollapsibleTag(final XmlTag tag) {
    final String name = StringUtil.toLowerCase(tag.getName());
    return tag.getLanguage() == XMLLanguage.INSTANCE ||
           "link".equals(name) || "br".equals(name) || "meta".equals(name) || "img".equals(name) || "input".equals(name) || "hr".equals(name) ||
           XmlExtension.isCollapsible(tag);
  }

  @Override
  public @NotNull @NonNls String getShortName() {
    return "CheckTagEmptyBody";
  }
}