package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class AddNoInspectionDocTagAction implements IntentionAction {
  private String myDisplayName;
  private String myID;
  private PsiElement myContext;

  public AddNoInspectionDocTagAction(LocalInspectionTool tool, PsiElement context) {
    myDisplayName = tool.getDisplayName();
    myID = tool.getID();
    myContext = context;
  }

  public AddNoInspectionDocTagAction(HighlightDisplayKey key, PsiElement context) {
    myDisplayName = HighlightDisplayKey.getDisplayNameByKey(key);
    myID = key.toString();
    myContext = context;
  }

  public String getText() {
    PsiDocCommentOwner container = getContainer();

    String subj = container instanceof PsiClass ? "class" : container instanceof PsiMethod ? "method" : "field";
    return "Suppress '" + myDisplayName + "' for " + subj;
  }

  private PsiDocCommentOwner getContainer() {
    PsiDocCommentOwner container;
    do {
      container = PsiTreeUtil.getParentOfType(myContext, PsiDocCommentOwner.class);
    }
    while (container instanceof PsiTypeParameter);
    return container;
  }

  public String getFamilyName() {
    return "Suppress inspection";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myContext.isValid() && myContext.getManager().isInProject(myContext) &&
           file.isWritable() && getContainer() != null;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiDocCommentOwner container = getContainer();
    PsiDocComment docComment = container.getDocComment();
    PsiManager manager = myContext.getManager();
    if (docComment == null) {
      String commentText = "/** @" + InspectionManagerEx.SUPPRESS_INSPECTIONS_TAG_NAME + " "+ myID + "*/";
      docComment = manager.getElementFactory().createDocCommentFromText(commentText, null);
      manager.getCodeStyleManager().reformat(docComment);
      PsiElement firstChild = container.getFirstChild();
      container.addBefore(docComment, firstChild);
      manager.getCodeStyleManager().reformatRange(container,
                                                  container.getTextRange().getStartOffset(),
                                                  firstChild.getTextRange().getStartOffset());
      return;
    }

    PsiDocTag noInspectionTag = docComment.findTagByName(InspectionManagerEx.SUPPRESS_INSPECTIONS_TAG_NAME);
    if (noInspectionTag != null) {
      String tagText = "@" + InspectionManagerEx.SUPPRESS_INSPECTIONS_TAG_NAME + " "
                           + noInspectionTag.getValueElement().getText() + ","+ myID;
      noInspectionTag.replace(manager.getElementFactory().createDocTagFromText(tagText, null));
    } else {
      String tagText = "@" + InspectionManagerEx.SUPPRESS_INSPECTIONS_TAG_NAME + " " + myID;
      docComment.add(manager.getElementFactory().createDocTagFromText(tagText, null));
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
