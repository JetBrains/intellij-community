package com.intellij.codeInsight.generation.actions;

import com.intellij.aspects.psi.PsiAspectFile;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.CommentByLineCommentHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.xml.XmlFile;

public class CommentByLineCommentAction extends BaseCodeInsightAction{
  public CommentByLineCommentAction() {
    setEnabledInModalContext(true);
  }

  protected CodeInsightActionHandler getHandler(){
    return new CommentByLineCommentHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return CommentByLineCommentHandler.getCommenter(file)!=null;
  }
}