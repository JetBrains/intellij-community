package com.intellij.vcsUtil;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PsiVcsSelectionProvider implements VcsSelectionProvider {
  @Nullable
  public VcsSelection getSelection(final VcsContext context) {
    final Editor editor = context.getEditor();
    if (editor == null) return null;
    PsiElement psiElement = TargetElementUtilBase.findTargetElement(editor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    if (psiElement == null) {
      return null;
    }
    if (!psiElement.isValid()) {
      return null;
    }
    if (psiElement instanceof PsiCompiledElement) {
      return null;
    }

    final String actionName;

    if (psiElement instanceof PsiClass) {
      actionName = VcsBundle.message("action.name.show.history.for.class");
    }
    else if (psiElement instanceof PsiField) {
      actionName = VcsBundle.message("action.name.show.history.for.field");
    }
    else if (psiElement instanceof PsiMethod) {
      actionName = VcsBundle.message("action.name.show.history.for.method");
    }
    else if (psiElement instanceof XmlTag) {
      actionName = VcsBundle.message("action.name.show.history.for.tag");
    }
    else if (psiElement instanceof XmlText) {
      actionName = VcsBundle.message("action.name.show.history.for.text");
    }
    else if (psiElement instanceof PsiCodeBlock) {
      actionName = VcsBundle.message("action.name.show.history.for.code.block");
    }
    else if (psiElement instanceof PsiStatement) {
      actionName = VcsBundle.message("action.name.show.history.for.statement");
    }
    else {
      return null;
    }

    TextRange textRange = psiElement.getTextRange();
    if (textRange == null) {
      return null;
    }

    VirtualFile virtualFile = psiElement.getContainingFile().getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    if (!virtualFile.isValid()) {
      return null;
    }

    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    return new VcsSelection(document, textRange, actionName);
  }
}
