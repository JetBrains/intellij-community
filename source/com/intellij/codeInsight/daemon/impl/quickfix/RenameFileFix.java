/*
 * @author ven
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

import java.io.IOException;

public class RenameFileFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.RenameFileFix");
  private final String myNewName;

  public RenameFileFix(String newName) {
    myNewName = newName;
  }

  public String getText() {
    return "Rename File";
  }

  public String getFamilyName() {
    return "Rename File";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!file.isValid()) return false;
    PsiDirectory directory = file.getContainingDirectory();
    VirtualFile vFile = file.getVirtualFile();
    String newName = myNewName + "." + vFile.getExtension();

    return directory.findFile(newName) == null;
  }


  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    VirtualFile vFile = file.getVirtualFile();
    String newName = myNewName + "." + vFile.getExtension();
    FileDocumentManager.getInstance().saveDocument(PsiDocumentManager.getInstance(file.getProject()).getDocument(file));
    try{
      vFile.rename(file.getManager(), newName);
    }
    catch(IOException e){
      throw new IncorrectOperationException(e.toString());
    }

    DaemonCodeAnalyzer.getInstance(project).updateVisibleHighlighters(editor);
  }

  public boolean startInWriteAction() {
    return true;
  }
}