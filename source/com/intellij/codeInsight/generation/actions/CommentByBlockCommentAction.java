package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.CommentByBlockCommentHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;

public class CommentByBlockCommentAction extends BaseCodeInsightAction {
  public CommentByBlockCommentAction() {
    setEnabledInModalContext(true);
  }

  protected CodeInsightActionHandler getHandler() {
    return new CommentByBlockCommentHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return CommentByBlockCommentHandler.getCommenter(file)!=null;
  }
}