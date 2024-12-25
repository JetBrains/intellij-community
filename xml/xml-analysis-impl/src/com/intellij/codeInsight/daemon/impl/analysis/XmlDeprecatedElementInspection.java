// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.RegexValidator;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlDeprecationOwnerDescriptor;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.util.XmlUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.string;

public class XmlDeprecatedElementInspection extends XmlSuppressableInspectionTool {

  @Language("RegExp")
  public String regexp = "(?i)deprecated.*";

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    Pattern pattern = Pattern.compile(regexp);
    return new XmlElementVisitor() {
      @Override
      public void visitXmlTag(@NotNull XmlTag tag) {
        if (checkDeprecated(tag.getDescriptor(), pattern)) {
          ASTNode nameNode = XmlChildRole.START_TAG_NAME_FINDER.findChild(tag.getNode());
          if (nameNode != null) {
            holder.registerProblem(nameNode.getPsi(), XmlAnalysisBundle.message("xml.inspections.the.tag.is.marked.as.deprecated"), ProblemHighlightType.LIKE_DEPRECATED);
          }
        }
      }

      @Override
      public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
        if (checkDeprecated(attribute.getDescriptor(), pattern)) {
          holder.registerProblem(attribute.getNameElement(), XmlAnalysisBundle.message(
            "xml.inspections.the.attribute.is.marked.as.deprecated"), ProblemHighlightType.LIKE_DEPRECATED);
        }
      }
    };
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      string("regexp", XmlAnalysisBundle.message("xml.options.label.regexp"), 30, new RegexValidator())
    );
  }

  private static boolean checkDeprecated(@Nullable PsiMetaData metaData, Pattern pattern) {
    if (metaData == null) return false;
    if (metaData instanceof XmlDeprecationOwnerDescriptor) {
      return ((XmlDeprecationOwnerDescriptor)metaData).isDeprecated();
    }
    
    PsiElement declaration = metaData.getDeclaration();
    if (!(declaration instanceof XmlTag tag)) return false;
    XmlComment comment = XmlUtil.findPreviousComment(declaration);
    if (comment != null && pattern.matcher(comment.getCommentText().trim()).matches()) return true;
    return checkTag(ArrayUtil.getFirstElement(tag.findSubTags("annotation", tag.getNamespace())), pattern);
  }

  private static boolean checkTag(XmlTag tag, Pattern pattern) {
    if (tag == null) return false;
    if ("documentation".equals(tag.getLocalName())) {
      String text = tag.getValue().getTrimmedText();
      return pattern.matcher(text).matches();
    }
    for (XmlTag subTag : tag.getSubTags()) {
      if (checkTag(subTag, pattern))
        return true;
    }
    return false;
  }
}
