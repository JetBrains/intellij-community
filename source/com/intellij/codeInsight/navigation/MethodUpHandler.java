
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

public class MethodUpHandler implements CodeInsightActionHandler {
  public void invoke(Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int caretOffset = editor.getCaretModel().getOffset();
    int caretLine = editor.getCaretModel().getLogicalPosition().line;
    PsiElement element = file;
    if (file instanceof XmlFile) {
      PsiElement elementAt = file.findElementAt(caretOffset);
      elementAt = PsiTreeUtil.getParentOfType(elementAt, XmlTag.class);
      if (elementAt != null) element = elementAt;
    }
    int[] offsets = MethodUpDownUtil.getNavigationOffsets(element);
    for(int i = offsets.length - 1; i >= 0; i--){
      int offset = offsets[i];
      if (offset < caretOffset){
        int line = editor.offsetToLogicalPosition(offset).line;
        if (line < caretLine){
          editor.getCaretModel().moveToOffset(offset);
          editor.getSelectionModel().removeSelection();
          editor.getScrollingModel().scrollToCaret(ScrollType.CENTER_UP);
          IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
          break;
        }
      }
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}