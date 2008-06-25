/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.xml.util;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim Mossienko
 */
public class CheckTagEmptyBodyInspection extends XmlSuppressableInspectionTool {
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override public void visitXmlTag(final XmlTag tag) {
        if (!CheckEmptyScriptTagInspection.isScriptTag(tag)) {
          final ASTNode child = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());

          if (child != null) {
            final ASTNode node = child.getTreeNext();

            if (node != null &&
                node.getElementType() == XmlTokenType.XML_END_TAG_START) {
              final LocalQuickFix localQuickFix = new ReplaceEmptyTagBodyByEmptyEndFix();
              holder.registerProblem(
                tag,
                XmlBundle.message("xml.inspections.tag.empty.body"),
                localQuickFix
              );
            }
          }
        }
      }
    };
  }

  @NotNull
  public String getGroupDisplayName() {
    return XmlInspectionGroupNames.XML_INSPECTIONS;
  }

  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("xml.inspections.check.tag.empty.body");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "CheckTagEmptyBody";
  }

  private static class ReplaceEmptyTagBodyByEmptyEndFix implements LocalQuickFix {
    @NotNull
    public String getName() {
      return XmlBundle.message("xml.inspections.replace.tag.empty.body.with.empty.end");
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
        final PsiElement tag = descriptor.getPsiElement();
      if (!CodeInsightUtilBase.prepareFileForWrite(tag.getContainingFile())) {
        return;
      }

      final ASTNode child = XmlChildRole.START_TAG_END_FINDER.findChild(tag.getNode());
      if (child == null) return;
      final int offset = child.getTextRange().getStartOffset();
      OpenFileDescriptor openFileDescriptor = new OpenFileDescriptor(project, tag.getContainingFile().getVirtualFile(), offset);
      final Editor editor = FileEditorManager.getInstance(project).openTextEditor(openFileDescriptor, true);


      new WriteCommandAction(project) {
        protected void run(final Result result) throws Throwable {
          editor.getDocument().replaceString(offset, tag.getTextRange().getEndOffset(),"/>");
        }
      }.execute();
    }
  }
}