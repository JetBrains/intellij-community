package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

/**
 * @author ven
 */
public class RenamePublicClassFix implements IntentionAction {
  private final PsiClass myClass;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.RenameFileFix");

  public RenamePublicClassFix(PsiClass aClass) {
    myClass = aClass;
  }

  public String getText() {
    return MessageFormat.format("Rename class ''{0}'' to ''{1}''",
        new Object[]{
          myClass.getName(),
          myClass.getContainingFile().getVirtualFile().getNameWithoutExtension()
        });
  }

  public String getFamilyName() {
    return "Rename Public Class";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myClass.isValid() &&
           file.getManager().getNameHelper().isIdentifier(file.getVirtualFile().getNameWithoutExtension());
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    LOG.assertTrue (file == myClass.getContainingFile());

    VirtualFile vFile = file.getVirtualFile();
    String newName = vFile.getNameWithoutExtension();
    RenameProcessor processor = new RenameProcessor(project, myClass, newName, false, false);
    processor.run();
  }

  public boolean startInWriteAction() {
    return true;
  }
}
