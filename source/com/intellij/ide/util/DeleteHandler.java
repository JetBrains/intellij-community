package com.intellij.ide.util;

import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.io.ReadOnlyAttributeUtil;

import java.io.IOException;
import java.util.ArrayList;

public class DeleteHandler {
  public static class DefaultDeleteProvider implements DeleteProvider {
    public boolean canDeleteElement(DataContext dataContext) {
      if ((Project)dataContext.getData(DataConstants.PROJECT) == null) return false;
      final PsiElement[] elements = getPsiElements(dataContext);
      if (elements == null) return false;
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }

    private PsiElement[] getPsiElements(DataContext dataContext) {
      PsiElement[] elements = (PsiElement[])dataContext.getData(DataConstantsEx.PSI_ELEMENT_ARRAY);
      if (elements == null) {
        final Object data = dataContext.getData(DataConstants.PSI_ELEMENT);
        if (data != null) {
          elements = new PsiElement[]{(PsiElement)data};
        }
        else {
          final Object data1 = dataContext.getData(DataConstants.PSI_FILE);
          if (data1 != null) {
            elements = new PsiElement[]{(PsiFile)data1};
          }
        }
      }
      return elements;
    }

    public void deleteElement(DataContext dataContext) {
      PsiElement[] elements = getPsiElements(dataContext);
      if (elements == null) return;
      Project project = (Project)dataContext.getData(DataConstants.PROJECT);
      if (project == null) return;
      LvcsAction action = LvcsIntegration.checkinFilesBeforeRefactoring(project, "Deleting");
      try {
        DeleteHandler.deletePsiElement(elements, project);
      }
      finally {
        LvcsIntegration.checkinFilesAfterRefactoring(project, action);
      }
    }

  }


  public static void deletePsiElement(final PsiElement[] elementsToDelete, final Project project) {
    if (elementsToDelete == null || elementsToDelete.length == 0) return;

    final PsiElement[] elements = DeleteUtil.filterElements(elementsToDelete);

    boolean safeDeleteApplicable = true;
    for (int i = 0; i < elements.length && safeDeleteApplicable; i++) {
      PsiElement element = elements[i];
      safeDeleteApplicable = element.isWritable() && SafeDeleteProcessor.validElement(element);
    }

    if (safeDeleteApplicable) {
      DeleteDialog dialog = new DeleteDialog(project, elements, new DeleteDialog.Callback() {
        public void run(final DeleteDialog dialog) {
          for (int i = 0; i < elements.length; i++) {
            PsiElement element = elements[i];
            if (!element.isWritable()) {
              RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, element);
              return;
            }
          }
          SafeDeleteProcessor.createInstance(project, new Runnable() {
            public void run() {
              dialog.close(DeleteDialog.CANCEL_EXIT_CODE);
            }
          }, elements, dialog.isSearchInComments(), dialog.isSearchInNonJava(), true).run(null);
        }
      }
      );
      dialog.show();
      if (!dialog.isOK()) return;
    }
    else {
      String warningMessage = DeleteUtil.generateWarningMessage("Delete", elements);

      int defaultOption;

      boolean anyDirectories = false;
      String directoryName = null;
      for (int i = 0; i < elementsToDelete.length; i++) {
        PsiElement psiElement = elementsToDelete[i];
        if (psiElement instanceof PsiDirectory) {
          anyDirectories = true;
          directoryName = ((PsiDirectory)psiElement).getName();
          break;
        }
      }
      if (anyDirectories) {
        if (elements.length == 1) {
          warningMessage += "\nAll files and subdirectories in \"" + directoryName +
                            "\" will be deleted.\nYou will not be able to undo this operation!";
        }
        else {
          warningMessage +=
          "\nAll files and subdirectories in the selected directory(s) will be deleted.\nYou will not be able to undo this operation!";
        }
        defaultOption = -1;
      }
      else {
        defaultOption = 0;
      }

      int result = Messages.showDialog(project, warningMessage, "Delete", new String[]{"OK", "Cancel"}, defaultOption,
                                       Messages.getQuestionIcon());
      if (result != 0) return;
    }

    CommandProcessor.getInstance().executeCommand(
      project, new Runnable() {
        public void run() {
          for (int i = 0; i < elements.length; i++) {
            final PsiElement elementToDelete = elements[i];

            if (elementToDelete instanceof PsiDirectory) {
              VirtualFile virtualFile = ((PsiDirectory)elementToDelete).getVirtualFile();
              if (virtualFile.getFileSystem() instanceof LocalFileSystem) {

                ArrayList readOnlyFiles = new ArrayList();
                getReadOnlyVirtualFiles(virtualFile, readOnlyFiles);

                if (readOnlyFiles.size() > 0) {
                  int _result = Messages.showOkCancelDialog(
                    project,
                    "Directory " + virtualFile.getPresentableUrl() + " contains read-only file(s). Delete it anyway?",
                    "Delete",
                    Messages.getQuestionIcon()
                  );
                  if (_result != 0) continue;

                  boolean success = true;
                  for (int j = 0; j < readOnlyFiles.size(); j++) {
                    VirtualFile file = (VirtualFile)readOnlyFiles.get(j);
                    success = clearReadOnlyFlag(file, project);
                    if (!success) break;
                  }
                  if (!success) continue;

                }

              }
            }
            else if (!elementToDelete.isWritable()) {
              final PsiFile file = elementToDelete.getContainingFile();
              if (file != null) {
                final VirtualFile virtualFile = file.getVirtualFile();
                if (virtualFile.getFileSystem() instanceof LocalFileSystem) {
                  int _result = MessagesEx.fileIsReadOnly(project, virtualFile)
                    .setTitle("Delete")
                    .appendMessage(" Delete it anyway?")
                    .askOkCancel();
                  if (_result != 0) continue;

                  boolean success = clearReadOnlyFlag(virtualFile, project);
                  if (!success) continue;
                }
              }
            }

            try {
              elementToDelete.checkDelete();
            }
            catch (IncorrectOperationException ex) {
              Messages.showMessageDialog(project, ex.getMessage(), "Error", Messages.getErrorIcon());
              continue;
            }

            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                try {
                  elementToDelete.delete();
                }
                catch (final IncorrectOperationException ex) {
                  ApplicationManager.getApplication().invokeLater(new Runnable() {
                                  public void run() {
                                    Messages.showMessageDialog(project, ex.getMessage(), "Error", Messages.getErrorIcon());
                                  }
                                });
                }
              }
            });
          }
        }
      },
      "Delete",
      null
    );
  }

  private static boolean clearReadOnlyFlag(final VirtualFile virtualFile, final Project project) {
    final boolean[] success = new boolean[1];
    CommandProcessor.getInstance().executeCommand(
      project, new Runnable() {
        public void run() {
          Runnable action = new Runnable() {
            public void run() {
              try {
                ReadOnlyAttributeUtil.setReadOnlyAttribute(virtualFile, false);
                success[0] = true;
              }
              catch (IOException e1) {
                Messages.showMessageDialog(project, e1.getMessage(), "Error", Messages.getErrorIcon());
              }
            }
          };
          ApplicationManager.getApplication().runWriteAction(action);
        }
      },
      "",
      null
    );
    return success[0];
  }

  /**
   * Fills readOnlyFiles with VirtualFiles
   */
  private static void getReadOnlyVirtualFiles(VirtualFile file, ArrayList readOnlyFiles) {
    if (!file.isWritable()) {
      readOnlyFiles.add(file);
    }
    if (file.isDirectory()) {
      VirtualFile[] children = file.getChildren();
      for (int i = 0; i < children.length; i++) {
        getReadOnlyVirtualFiles(children[i], readOnlyFiles);
      }
    }
  }

  public static boolean shouldEnableDeleteAction(PsiElement[] elements) {
    if (elements == null || elements.length == 0) return false;
    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];
      if (element instanceof PsiCompiledElement) {
        return false;
      }
    }
    return true;
  }
}
