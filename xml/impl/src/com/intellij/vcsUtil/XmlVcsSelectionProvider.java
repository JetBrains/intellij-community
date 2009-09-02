package com.intellij.vcsUtil;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;

/**
 * @author yole
 */
public class XmlVcsSelectionProvider implements VcsSelectionProvider {
  public VcsSelection getSelection(VcsContext context) {
    final Editor editor = context.getEditor();
      if (editor == null) return null;
      PsiElement psiElement = TargetElementUtilBase.findTargetElement(editor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
      if (psiElement == null || !psiElement.isValid()) {
        return null;
      }

      final String actionName;

      if (psiElement instanceof XmlTag) {
        actionName = VcsBundle.message("action.name.show.history.for.tag");
      }
      else if (psiElement instanceof XmlText) {
        actionName = VcsBundle.message("action.name.show.history.for.text");
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
