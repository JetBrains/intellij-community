package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.RenameFileFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.UnknownFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.jsp.WebDirectoryElement;
import com.intellij.util.IncorrectOperationException;

import java.util.Arrays;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 18, 2005
 * Time: 10:40:31 PM
 * To change this template use File | Settings | File Templates.
 */
public class FileReferenceQuickFixProvider {
  public static void registerQuickFix(final HighlightInfo info, final PsiReference reference) {
    final FileReference fileReference = ((FileReference)reference);
    final FileReferenceSet fileReferenceSet = fileReference.getFileReferenceSet();
    int index = Arrays.asList(fileReferenceSet.getAllReferences()).indexOf(fileReference);

    if (index < 0) return;
    final String newFileName = fileReference.getCanonicalText();
    final PsiDirectory directory;

    if(index > 0) {
      PsiElement context = fileReferenceSet.getReference(index - 1).resolve();
      if (context == null) return;
      if (context instanceof PsiDirectory) directory = (PsiDirectory)context;
      else if (context instanceof WebDirectoryElement) {
        final VirtualFile originalFile = ((WebDirectoryElement)context).getOriginalVirtualFile();
        if (originalFile != null && originalFile.isDirectory()) {
          directory = reference.getElement().getManager().findDirectory(originalFile);
          if (directory == null) return;
        } else {
          return;
        }
      }
      else {
        return;
      }
    } else {
      final Collection<PsiElement> defaultContexts = fileReferenceSet.getDefaultContexts(reference.getElement());
      final PsiElement psiElement = (!defaultContexts.isEmpty())?defaultContexts.iterator().next():null;
      
      if (psiElement instanceof PsiDirectory) {
        directory = (PsiDirectory)psiElement;
      } else if (psiElement instanceof WebDirectoryElement) {
        final VirtualFile originalFile = ((WebDirectoryElement)psiElement).getOriginalVirtualFile();
        
        if (originalFile != null && originalFile.isDirectory()) {
          directory = reference.getElement().getManager().findDirectory(originalFile);
          if (directory == null) return;
        } else {
          return;
        }
      } else {
        return;
      }
    }

    boolean differentCase = false;

    if (fileReferenceSet.isCaseSensitive()) {
      boolean original = fileReferenceSet.isCaseSensitive();
      try {
        fileReferenceSet.setCaseSensitive(false);
        final PsiElement psiElement = fileReference.resolve();

        if (psiElement instanceof PsiNamedElement) {
          final String existingElementName = ((PsiNamedElement)psiElement).getName();

          differentCase = true;
          QuickFixAction.registerQuickFixAction(
            info,
            new RenameFileReferenceIntentionAction(existingElementName, fileReference),
            null
          );

          QuickFixAction.registerQuickFixAction(
            info,
            new RenameFileFix(newFileName),
            null
          );
        }
      } finally {
        fileReferenceSet.setCaseSensitive(original);
      }
    }

    if (differentCase && SystemInfo.isWindows) return;

    final boolean isdirectory;
    final ReferenceType type = fileReference.getType();

    if (type.isAssignableTo(ReferenceType.DIRECTORY)) {
      // directory
      try {
        directory.checkCreateSubdirectory(newFileName);
      } catch(IncorrectOperationException ex) {
        return;
      }
      isdirectory = true;
    } else {
      FileType ft = FileTypeManager.getInstance().getFileTypeByFileName(newFileName);
      if (ft instanceof UnknownFileType) return;

      try {
        directory.checkCreateFile(newFileName);
      } catch(IncorrectOperationException ex) {
        return;
      }

      isdirectory = false;
    }

    QuickFixAction.registerQuickFixAction(
      info,
      new IntentionAction() {
        public String getText() {
          return isdirectory ?
                 QuickFixBundle.message("create.directory.text", newFileName) :
                 QuickFixBundle.message("create.file.text", newFileName);
        }

        public String getFamilyName() {
          return QuickFixBundle.message("create.file.family");
        }

        public boolean isAvailable(Project project, Editor editor, PsiFile file) {
          return true;
        }

        public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
          if (isdirectory) {
            PsiDirectory newdirectory = directory.createSubdirectory(newFileName);
          } else {
            PsiFile newfile = directory.createFile(newFileName);
            FileEditorManager.getInstance(directory.getProject()).openFile(newfile.getVirtualFile(), true);
          }
        }

        public boolean startInWriteAction() {
          return true;
        }
      },
      null
    );
  }

  private static class RenameFileReferenceIntentionAction implements IntentionAction {
    private final String myExistingElementName;
    private final FileReference myFileReference;

    public RenameFileReferenceIntentionAction(final String existingElementName, final FileReference fileReference) {
      myExistingElementName = existingElementName;
      myFileReference = fileReference;
    }

    public String getText() {
      return QuickFixBundle.message("rename.file.reference.text", myExistingElementName);
    }

    public String getFamilyName() {
      return QuickFixBundle.message("rename.file.reference.family");
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      if (!CodeInsightUtil.prepareFileForWrite(file)) return;
      myFileReference.handleElementRename(myExistingElementName);
    }

    public boolean startInWriteAction() {
      return true;
    }
  }
}
