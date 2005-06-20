package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: May 13, 2005
 */
public class AddNoInspectionAllForClassAction extends AddNoInspectionDocTagAction{
  public AddNoInspectionAllForClassAction(final PsiElement context) {
    super("ALL", "ALL", context);
  }

  @Nullable protected PsiDocCommentOwner getContainer() {
    PsiDocCommentOwner container = super.getContainer();
    if (container == null){
      return null;
    }
    while (container != null ) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(container, PsiClass.class);
      if (parentClass == null && container instanceof PsiClass){
        return container;
      }
      container = parentClass;
    }
    return container;
  }

  public String getText() {
    return "Suppress all inspections for class";
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiDocCommentOwner container = getContainer();
    final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project)
      .ensureFilesWritable(new VirtualFile[]{container.getContainingFile().getVirtualFile()});
    if (status.hasReadonlyFiles()) return;
    PsiDocComment docComment = container.getDocComment();
    if (docComment != null){
      PsiDocTag noInspectionTag = docComment.findTagByName(InspectionManagerEx.SUPPRESS_INSPECTIONS_TAG_NAME);
      if (noInspectionTag != null) {
        String tagText = "@" + InspectionManagerEx.SUPPRESS_INSPECTIONS_TAG_NAME + " ALL";
        noInspectionTag.replace(myContext.getManager().getElementFactory().createDocTagFromText(tagText, null));
        return;
      }
    }
    super.invoke(project, editor, file);
  }
}
