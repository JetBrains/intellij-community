// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class XmlDeprecatedElementInspection extends XmlSuppressableInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
        if (checkDeprecated(tag.getDescriptor())) {
          ASTNode nameNode = XmlChildRole.START_TAG_NAME_FINDER.findChild(tag.getNode());
          if (nameNode != null) {
            holder.registerProblem(nameNode.getPsi(), "The tag is marked as deprecated", ProblemHighlightType.LIKE_DEPRECATED);
          }
        }
      }

      @Override
      public void visitXmlAttribute(XmlAttribute attribute) {
        if (checkDeprecated(attribute.getDescriptor())) {
          holder.registerProblem(attribute.getNameElement(), "The attribute is marked as deprecated", ProblemHighlightType.LIKE_DEPRECATED);
        }
      }
    };
  }

  private static boolean checkDeprecated(@Nullable PsiMetaData metaData) {
    if (metaData == null) return false;
    PsiElement declaration = metaData.getDeclaration();
    if (declaration == null) return false;
    PsiElement comment = XmlUtil.findPreviousComment(declaration);
    if (comment == null) return false;
    return StringUtil.trimStart(comment.getText(), "<!--").toLowerCase(Locale.ENGLISH).trim().startsWith("deprecated");
  }
}
