package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
class AddNoInspectionCommentAction implements IntentionAction {
  LocalInspectionTool myTool;
  PsiElement myContext;
  private static final String COMMENT_START_TEXT = "//noinspection ";

  public AddNoInspectionCommentAction(LocalInspectionTool tool, PsiElement context) {
    myTool = tool;
    myContext = context;
  }

  public String getText() {
    return "Suppress '" + myTool.getID() + "' for statement";
  }

  private PsiStatement getContainer() {
    return PsiTreeUtil.getParentOfType(myContext, PsiStatement.class);
  }

  public String getFamilyName() {
    return "Suppress inspection";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myContext.isValid() && myContext.getManager().isInProject(myContext) &&
           file.isWritable() && getContainer() != null;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiStatement container = getContainer();
    PsiElement prev = PsiTreeUtil.skipSiblingsBackward(container, new Class[]{PsiWhiteSpace.class});
    PsiElementFactory factory = myContext.getManager().getElementFactory();
    if (prev instanceof PsiComment) {
      String text = prev.getText();
      if (text.startsWith(COMMENT_START_TEXT)) {
        prev.replace(factory.createCommentFromText(text + "," + myTool.getID(), null));
        return;
      }
    }

    container.addAfter(factory.createCommentFromText(COMMENT_START_TEXT +  myTool.getID(), null), null);
    QuickFixAction.spoilDocument(project, file);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
