package com.intellij.refactoring.copy;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.commander.Commander;
import com.intellij.ide.commander.CommanderPanel;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.structureView.StructureViewFactory;
import com.intellij.ide.util.DeleteUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import java.util.HashSet;

public class CopyHandler {
  public static final int NOT_SUPPORTED = 0;
  public static final int CLASS = 1;
  public static final int FILES = 2;
  public static final int DIRECTORIES = 3;

  public static boolean canCopy(PsiElement[] elements) {
    int moveType = getCopyType(elements);
    return moveType != NOT_SUPPORTED;
  }

  private static int getCopyType(PsiElement[] elements) {
    if (elements.length == 0) {
      return NOT_SUPPORTED;
    }

    if (elements.length == 1) {
      if (elements[0] instanceof PsiClass && elements[0].getParent() instanceof PsiFile) {
        return CLASS;
      }
    }

    if (canCopyFiles(elements)) {
      return FILES;
    }

    if (canCopyDirectories(elements)) {
      return DIRECTORIES;
    }

    return NOT_SUPPORTED;
  }

  private static boolean canCopyFiles(PsiElement[] elements) {
    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];
      if (!(element instanceof PsiFile) || (element instanceof PsiJavaFile)) {
        return false;
      }
    }

    // the second 'for' statement is for effectivity - to prevent creation of the 'names' array
    HashSet names = new HashSet();
    for (int i = 0; i < elements.length; i++) {
      PsiFile file = (PsiFile)elements[i];
      String name = file.getName();
      if (names.contains(name)) {
        return false;
      }

      names.add(name);
    }

    return true;
  }

  private static boolean canCopyDirectories(PsiElement[] elements) {
    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];
      if (!(element instanceof PsiDirectory)) {
        return false;
      }
    }

    for (int i = 0; i < elements.length; i++) {
      PsiDirectory directory = (PsiDirectory)elements[i];

      if (hasPackages(directory)) {
        return false;
      }
    }

    PsiElement[] filteredElements = DeleteUtil.filterElements(elements);
    if (filteredElements.length != elements.length) {
      // there are nested dirs
      return false;
    }

    return true;
  }

  private static boolean hasPackages(PsiDirectory directory) {
    if (directory.getPackage() != null) {
      return true;
    }
    PsiDirectory[] subdirectories = directory.getSubdirectories();
    for (int i = 0; i < subdirectories.length; i++) {
      PsiDirectory subdirectory = subdirectories[i];
      if (hasPackages(subdirectory)) {
        return true;
      }
    }
    return false;
  }


  public static void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    int type = getCopyType(elements);
    if (type == NOT_SUPPORTED) {
      return;
    }

    Project project = elements[0].getProject();

    if (type == CLASS) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");      
      PsiClass aClass = (PsiClass)elements[0];
      if (defaultTargetDirectory == null) {
        final PsiFile containingFile = aClass.getContainingFile();
        if (containingFile != null) { // ???
          defaultTargetDirectory = containingFile.getContainingDirectory();
        }
      }
      CopyClassDialog dialog = new CopyClassDialog(aClass, defaultTargetDirectory, project, false);
      dialog.setTitle("Copy Class");
      dialog.show();
      if (dialog.isOK()) {
        PsiDirectory targetDirectory = dialog.getTargetDirectory();
        String className = dialog.getClassName();
        copyClassImpl(className, project, aClass, targetDirectory, "Copy class", false);
      }
    }
    else if (type == FILES || type == DIRECTORIES) {
      if (defaultTargetDirectory == null) {
        defaultTargetDirectory = getCommonParentDirectory(elements);
      }

      CopyFilesOrDirectoriesDialog dialog = new CopyFilesOrDirectoriesDialog(elements, defaultTargetDirectory, project, false);
      dialog.show();
      if (dialog.isOK()) {
        String newName = elements.length == 1 ? dialog.getNewName() : null;
        copyImpl(elements, newName, dialog.getTargetDirectory(), false);
      }
    }
    else {
      throw new IllegalArgumentException("wrong type " + type);
    }
  }

  private static PsiDirectory getCommonParentDirectory(PsiElement[] elements){
    PsiDirectory result = null;

    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];
      PsiDirectory directory;

      if (element instanceof PsiDirectory) {
        directory = (PsiDirectory)element;
        directory = directory.getParentDirectory();
      }
      else if (element instanceof PsiFile) {
        directory = ((PsiFile)element).getContainingDirectory();
      }
      else {
        throw new IllegalArgumentException("unexpected element " + element);
      }

      if (directory == null) continue;

      if (result == null) {
        result = directory;
      }
      else {
        if (PsiTreeUtil.isAncestor(directory, result, true)) {
          result = directory;
        }
      }
    }

    return result;
  }

  public static void doClone(PsiElement element) {
    PsiElement[] elements = new PsiElement[]{element};
    int type = getCopyType(elements);
    if (type == NOT_SUPPORTED) {
      return;
    }

    Project project = element.getProject();

    PsiDirectory targetDirectory;
    if (element instanceof PsiFile) {
      targetDirectory = ((PsiFile)element).getContainingDirectory();
    }
    else if (element instanceof PsiDirectory) {
      targetDirectory = ((PsiDirectory)element).getParentDirectory();
    }
    else {
      PsiFile file = element.getContainingFile();
      targetDirectory = file.getContainingDirectory();
    }

    if (type == CLASS) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");
      PsiClass aClass = (PsiClass)element;

      CopyClassDialog dialog = new CopyClassDialog(aClass, null, project, true);
      dialog.setTitle("Clone Class");
      dialog.show();
      if (dialog.isOK()) {
        String className = dialog.getClassName();
        copyClassImpl(className, project, aClass, targetDirectory, "Clone class", true);
      }
    }
    else if (type == FILES || type == DIRECTORIES) {
      CopyFilesOrDirectoriesDialog dialog = new CopyFilesOrDirectoriesDialog(elements, null, project, true);
      dialog.show();
      if (dialog.isOK()) {
        String newName = dialog.getNewName();
        copyImpl(elements, newName, targetDirectory, true);
      }
    }
    else {
      throw new IllegalArgumentException("wrong type " + type);
    }
  }

  private static void copyClassImpl(final String copyClassName, final Project project, final PsiElement psiElement, final PsiDirectory targetDirectory, String commandName, final boolean selectInActivePanel) {
    if (copyClassName == null || copyClassName.length() == 0) return;
    final boolean[] result = new boolean[] {false};
    Runnable command = new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            try {
              ChangeContextUtil.encodeContextInfo(psiElement.getNavigationElement(), true);
              PsiClass classCopy = (PsiClass) psiElement.getNavigationElement().copy();
              ChangeContextUtil.clearContextInfo(psiElement);
              classCopy.setName(copyClassName);
              PsiClass newClass = (PsiClass) targetDirectory.add(classCopy);
              ChangeContextUtil.decodeContextInfo(newClass, null, null);
              final PsiManager psiManager = PsiManager.getInstance(project);
              PsiReference[] refs = psiManager.getSearchHelper().findReferences(psiElement, new LocalSearchScope(newClass), true);

              for (int i = 0; i < refs.length; i++) {
                final PsiReference ref = refs[i];
                if (!ref.getElement().isValid()) continue;
                ref.bindToElement(newClass);
              }


              updateSelectionInActiveProjectView(newClass, project, selectInActivePanel);
              EditorHelper.openInEditor(newClass);

              result[0] = true;
            } catch (final IncorrectOperationException ex) {
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog(project, ex.getMessage(), "Error", Messages.getErrorIcon());
                }
              });
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    };
    CommandProcessor processor = CommandProcessor.getInstance();
    processor.executeCommand(project, command, commandName, null);

    if (result[0]) {
      ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
        public void run() {
          ToolWindowManager.getInstance(project).activateEditorComponent();
        }
      });
    }
  }

  private static void updateSelectionInActiveProjectView(PsiElement newElement, Project project, boolean selectInActivePanel) {
    String id = ToolWindowManager.getInstance(project).getActiveToolWindowId();
    if (ToolWindowId.COMMANDER.equals(id)) {
      Commander commander = Commander.getInstance(project);
      CommanderPanel panel = selectInActivePanel ? commander.getActivePanel() : commander.getInactivePanel();
      panel.getBuilder().selectElement(newElement);
    } else if (ToolWindowId.PROJECT_VIEW.equals(id)) {
      ProjectView.getInstance(project).selectPsiElement(newElement, true);
    } else if (ToolWindowId.STRUCTURE_VIEW.equals(id)) {
      VirtualFile virtualFile = newElement.getContainingFile().getVirtualFile();
      FileEditor editor = FileEditorManager.getInstance(newElement.getProject()).getSelectedEditor(virtualFile);
      StructureViewFactory.getInstance(project).getStructureView().select(newElement,editor, true);
    }
  }

  /**
   * @param elementToCopy PsiFile or PsiDirectory
   * @param newName can be not null only if elements.length == 1
   * @return first copied PsiFile (recursivly); null if no PsiFiles copied
   */
  private static PsiFile copyToDirectory(final PsiElement elementToCopy, String newName, final PsiDirectory targetDirectory) throws IncorrectOperationException{
    if (elementToCopy instanceof PsiFile) {
      PsiFile file = (PsiFile)elementToCopy;
      if (newName != null) {
        file = (PsiFile)file.copy();
        file = (PsiFile) file.setName(newName);
      }
      PsiFile newFile = (PsiFile)targetDirectory.add(file);
      return newFile;
    }
    else if (elementToCopy instanceof PsiDirectory) {
      PsiDirectory directory = (PsiDirectory)elementToCopy;
      if (directory.equals(targetDirectory)) {
        return null;
      }
      PsiDirectory subdirectory = targetDirectory.createSubdirectory(newName == null ? directory.getName() : newName);
      PsiFile firstFile = null;
      PsiElement[] children = directory.getChildren();
      for (int i = 0; i < children.length; i++) {
        PsiElement child = children[i];
        PsiFile f = copyToDirectory(child, null, subdirectory);
        if (firstFile == null) {
          firstFile = f;
        }
      }
      return firstFile;
    }
    else {
      throw new IllegalArgumentException("unexpected elementToCopy: " + elementToCopy);
    }
  }


  /**
   *
   * @param elements
   * @param newName can be not null only if elements.length == 1
   * @param targetDirectory
   */
  private static void copyImpl(final PsiElement[] elements, final String newName, final PsiDirectory targetDirectory, final boolean doClone) {
    if (doClone && elements.length != 1) {
      throw new IllegalArgumentException("invalid number of elements to clone:" + elements.length);
    }

    if (newName != null && elements.length != 1) {
      throw new IllegalArgumentException("no new name should be set; number of elements is: " + elements.length);
    }

    final Project project = targetDirectory.getProject();
    Runnable command = new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            try {
              PsiFile firstFile = null;

              for (int i = 0; i < elements.length; i++) {
                PsiElement element = elements[i];

                PsiFile f = copyToDirectory(element, newName, targetDirectory);
                if (firstFile == null) {
                  firstFile = f;
                }
              }

              if (firstFile != null) {
                updateSelectionInActiveProjectView(firstFile, project, doClone);
                if (!(firstFile instanceof PsiBinaryFile)){
                  EditorHelper.openInEditor(firstFile);
                  ApplicationManager.getApplication().invokeLater(new Runnable() {
                                  public void run() {
                                    ToolWindowManager.getInstance(project).activateEditorComponent();
                                  }
                                });
                }
              }

            } catch (final IncorrectOperationException ex) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                          public void run() {
                            Messages.showMessageDialog(project, ex.getMessage(), "Error", Messages.getErrorIcon());
                          }
                        });
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    };
    CommandProcessor.getInstance().executeCommand(project, command, doClone ? "Clone files/directories" : "Copy files/directories", null);
  }

}
