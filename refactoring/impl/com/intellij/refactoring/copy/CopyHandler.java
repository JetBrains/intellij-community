package com.intellij.refactoring.copy;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.commander.Commander;
import com.intellij.ide.commander.CommanderPanel;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.structureView.StructureViewFactoryEx;
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
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

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
    for (PsiElement element : elements) {
      if (element instanceof JspClass || element instanceof JspHolderMethod) return NOT_SUPPORTED;
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
    for (PsiElement element : elements) {
      if (!(element instanceof PsiFile) || (element instanceof PsiJavaFile && !(PsiUtil.isInJspFile(element)))) {
        return false;
      }
    }

    // the second 'for' statement is for effectivity - to prevent creation of the 'names' array
    HashSet<String> names = new HashSet<String>();
    for (PsiElement element1 : elements) {
      PsiFile file = (PsiFile)element1;
      String name = file.getName();
      if (names.contains(name)) {
        return false;
      }

      names.add(name);
    }

    return true;
  }

  private static boolean canCopyDirectories(PsiElement[] elements) {
    for (PsiElement element : elements) {
      if (!(element instanceof PsiDirectory)) {
        return false;
      }
    }

    for (PsiElement element1 : elements) {
      PsiDirectory directory = (PsiDirectory)element1;

      if (hasPackages(directory)) {
        return false;
      }
    }

    PsiElement[] filteredElements = DeleteUtil.filterElements(elements);
    return filteredElements.length == elements.length;

  }

  private static boolean hasPackages(PsiDirectory directory) {
    if (directory.getPackage() != null) {
      return true;
    }
    PsiDirectory[] subdirectories = directory.getSubdirectories();
    for (PsiDirectory subdirectory : subdirectories) {
      if (hasPackages(subdirectory)) {
        return true;
      }
    }
    return false;
  }


  public static void doCopy(PsiElement[] elements, final PsiPackage defaultPackage, PsiDirectory defaultTargetDirectory) {
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
      CopyClassDialog dialog = new CopyClassDialog(aClass, defaultTargetDirectory, defaultPackage, project, false);
      dialog.setTitle(RefactoringBundle.message("copy.handler.copy.class"));
      dialog.show();
      if (dialog.isOK()) {
        PsiDirectory targetDirectory = dialog.getTargetDirectory();
        String className = dialog.getClassName();
        copyClassImpl(className, project, aClass, targetDirectory, RefactoringBundle.message("copy.handler.copy.class"), false);
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

  @Nullable
  private static PsiDirectory getCommonParentDirectory(PsiElement[] elements){
    PsiDirectory result = null;

    for (PsiElement element : elements) {
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

      CopyClassDialog dialog = new CopyClassDialog(aClass, null, null,project, true);
      dialog.setTitle(RefactoringBundle.message("copy.handler.clone.class"));
      dialog.show();
      if (dialog.isOK()) {
        String className = dialog.getClassName();
        copyClassImpl(className, project, aClass, targetDirectory, RefactoringBundle.message("copy.handler.clone.class"), true);
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

  private static void copyClassImpl(final String copyClassName, final Project project, final PsiClass aClass, final PsiDirectory targetDirectory, String commandName, final boolean selectInActivePanel) {
    if (copyClassName == null || copyClassName.length() == 0) return;
    final boolean[] result = new boolean[] {false};
    Runnable command = new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            try {
              ChangeContextUtil.encodeContextInfo(aClass.getNavigationElement(), true);
              PsiClass classCopy = (PsiClass) aClass.getNavigationElement().copy();
              ChangeContextUtil.clearContextInfo(aClass);
              classCopy.setName(copyClassName);
              PsiClass newClass = (PsiClass) targetDirectory.add(classCopy);
              ChangeContextUtil.decodeContextInfo(newClass, null, null);
              final PsiManager psiManager = PsiManager.getInstance(project);
              PsiReference[] refs = psiManager.getSearchHelper().findReferences(aClass, new LocalSearchScope(newClass), true);

              for (final PsiReference ref : refs) {
                PsiElement element = ref.getElement();
                if (!element.isValid()) continue;
                element = ref.bindToElement(newClass);
                final PsiElement parent = element.getParent();
                if (parent instanceof PsiReferenceExpression && element.equals(((PsiReferenceExpression)parent).getQualifierExpression())) {
                  final PsiReferenceExpression refExpr = ((PsiReferenceExpression)parent);
                  final PsiElement resolved = refExpr.resolve();
                  if (resolved == null) continue;
                  final PsiReferenceExpression copy;
                  if (parent.getParent() instanceof PsiMethodCallExpression){
                    copy = ((PsiMethodCallExpression)parent.getParent().copy()).getMethodExpression();
                  } else {
                    copy = (PsiReferenceExpression)refExpr.copy();
                  }

                  final PsiExpression qualifier = copy.getQualifierExpression();
                  assert qualifier != null;
                  qualifier.delete();
                  if (copy.resolve() == resolved) {
                    element.delete();
                  }
                }
              }

              updateSelectionInActiveProjectView(newClass, project, selectInActivePanel);
              EditorHelper.openInEditor(newClass);

              result[0] = true;
            } catch (final IncorrectOperationException ex) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog(project, ex.getMessage(),
                                             RefactoringBundle.message("error.title"), Messages.getErrorIcon());
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
      panel.getBuilder().selectElement(newElement,PsiUtil.getVirtualFile(newElement));
    } else if (ToolWindowId.PROJECT_VIEW.equals(id)) {
      ProjectView.getInstance(project).selectPsiElement(newElement, true);
    } else if (ToolWindowId.STRUCTURE_VIEW.equals(id)) {
      VirtualFile virtualFile = newElement.getContainingFile().getVirtualFile();
      FileEditor editor = FileEditorManager.getInstance(newElement.getProject()).getSelectedEditor(virtualFile);
      StructureViewFactoryEx.getInstance(project).getStructureViewWrapper().selectCurrentElement(editor, true);
    }
  }

  /**
   * @param elementToCopy PsiFile or PsiDirectory
   * @param newName can be not null only if elements.length == 1
   * @return first copied PsiFile (recursivly); null if no PsiFiles copied
   */
  @Nullable
  private static PsiFile copyToDirectory(final PsiElement elementToCopy, String newName, final PsiDirectory targetDirectory) throws IncorrectOperationException{
    if (elementToCopy instanceof PsiFile) {
      PsiFile file = (PsiFile)elementToCopy;
      if (newName != null) {
        file = (PsiFile)file.copy();
        file = (PsiFile) file.setName(newName);
      }
      return (PsiFile)targetDirectory.add(file);
    }
    else if (elementToCopy instanceof PsiDirectory) {
      PsiDirectory directory = (PsiDirectory)elementToCopy;
      if (directory.equals(targetDirectory)) {
        return null;
      }
      PsiDirectory subdirectory = targetDirectory.createSubdirectory(newName == null ? directory.getName() : newName);
      PsiFile firstFile = null;
      PsiElement[] children = directory.getChildren();
      for (PsiElement child : children) {
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

              for (PsiElement element : elements) {
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
                            Messages.showMessageDialog(project, ex.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
                          }
                        });
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    };
    CommandProcessor.getInstance().executeCommand(project, command, doClone ?
                                                                    RefactoringBundle.message("copy,handler.clone.files.directories") :
                                                                    RefactoringBundle.message("copy.handler.copy.files.directories"), null);
  }

}
