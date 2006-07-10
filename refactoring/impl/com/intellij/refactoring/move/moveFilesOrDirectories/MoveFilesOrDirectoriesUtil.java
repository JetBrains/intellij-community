package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.ide.util.DeleteUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.ChangeContextUtil;

import java.util.Arrays;
import java.util.HashSet;

public class MoveFilesOrDirectoriesUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil");

  private MoveFilesOrDirectoriesUtil() {}

  // Does not process non-code usages!
  public static PsiDirectory doMoveDirectory(PsiDirectory aDirectory, PsiDirectory destDirectory) throws IncorrectOperationException{
    PsiManager manager = aDirectory.getManager();
    // do actual move
    manager.moveDirectory(aDirectory, destDirectory);
    return aDirectory;
  }

  // Does not process non-code usages!
  public static PsiFile doMoveFile(PsiFile file, PsiDirectory newDirectory) throws IncorrectOperationException{
    ChangeContextUtil.encodeFileReferences(file);
    PsiManager manager = file.getManager();
    // the class is already there, this is true when multiple classes are defined in the same file
    if (!newDirectory.equals(file.getContainingDirectory())) {
      // do actual move
      manager.moveFile(file, newDirectory);
    }
    ChangeContextUtil.decodeFileReferences(file);
    return file;
  }

  /**
   * @param elements should contain PsiDirectories or PsiFiles only
   */
  public static void doMove(final Project project, final PsiElement[] elements, PsiDirectory initialTargetElement, final MoveCallback moveCallback) {
    for (PsiElement element : elements) {
      if (!(element instanceof PsiFile) && !(element instanceof PsiDirectory)) {
        throw new IllegalArgumentException("unexpected element type: " + element);
      }
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(elements))) return;

    final PsiDirectory initialTargetDirectory = getInitialTargetDirectory(initialTargetElement, elements);

    final MoveFilesOrDirectoriesDialog.Callback doRun = new MoveFilesOrDirectoriesDialog.Callback() {
      public void run(final MoveFilesOrDirectoriesDialog moveDialog) {
        final PsiDirectory targetDirectory = moveDialog.getTargetDirectory();

        LOG.assertTrue(targetDirectory != null);

        PsiManager manager = PsiManager.getInstance(project);
        try {
          for (PsiElement psiElement : elements) {
            manager.checkMove(psiElement, targetDirectory);
          }

          new MoveFilesOrDirectoriesProcessor(
            project,
            elements,
            targetDirectory,
            false,
            false,
            moveCallback,
            new Runnable() {
              public void run() {
                moveDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
              }
            }).run();
        }
        catch (IncorrectOperationException e) {
          String helpId = HelpID.getMoveHelpID(elements[0]);
          CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), e.getMessage(), helpId, project);
        }
      }
    };

    final MoveFilesOrDirectoriesDialog moveDialog = new MoveFilesOrDirectoriesDialog(project, doRun);
    boolean searchInComments = RefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS;
    boolean searchForTextOccurences = RefactoringSettings.getInstance().MOVE_SEARCH_FOR_TEXT;
    moveDialog.setData(
      elements,
      initialTargetDirectory,
      searchInComments,
      searchForTextOccurences,
      HelpID.getMoveHelpID(elements[0])
    );
    moveDialog.show();
  }

  private static PsiDirectory getCommonDirectory(PsiElement[] movedElements) {
    PsiDirectory commonDirectory = null;

    for (PsiElement movedElement : movedElements) {
      final PsiFile containingFile = movedElement.getContainingFile();
      if (containingFile != null) {
        final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
        if (containingDirectory != null) {
          if (commonDirectory == null) {
            commonDirectory = containingDirectory;
          }
          else {
            if (commonDirectory != containingDirectory) {
              return null;
            }
          }
        }
      }
    }
    if(commonDirectory != null) {
      return commonDirectory;
    }
    else {
      return null;
    }
  }

  private static PsiDirectory getInitialTargetDirectory(PsiDirectory initialTargetElement, final PsiElement[] movedElements) {
    PsiDirectory initialTargetDirectory = initialTargetElement;
    if (initialTargetDirectory == null) {
      if (movedElements != null) {
        final PsiDirectory commonDirectory = getCommonDirectory(movedElements);
        if (commonDirectory != null) {
          initialTargetDirectory = commonDirectory;
        } else {
          initialTargetDirectory = getContainerDirectory(movedElements[0]);
        }
      }
    }
    return initialTargetDirectory;
  }

  private static PsiDirectory getContainerDirectory(final PsiElement psiElement) {
    if (psiElement instanceof PsiDirectory) {
      return (PsiDirectory)psiElement;
    }
    else if (psiElement != null) {
      return psiElement.getContainingFile().getContainingDirectory();
    }
    else {
      return null;
    }
  }

  public static boolean canMoveFiles(PsiElement[] elements) {
    if (elements == null) {
      throw new IllegalArgumentException("elements cannot be null");
    }
    for (PsiElement element : elements) {
      if (!(element instanceof PsiFile) || (element instanceof PsiJavaFile && !(PsiUtil.isInJspFile(element)))) {
        return false;
      }
    }

    // the second 'for' statement is for effectivity - to prevent creation of the 'names' array
    HashSet<String> names = new HashSet<String>();
    for (PsiElement element : elements) {
      PsiFile file = (PsiFile)element;
      String name = file.getName();
      if (names.contains(name)) {
        return false;
      }

      names.add(name);
    }

    return true;
  }

  public static boolean canMoveOrRearrangePackages(PsiElement[] elements) {
    if (elements.length == 0) return false;
    final Project project = elements[0].getProject();
    if (ProjectRootManager.getInstance(project).getContentSourceRoots().length == 1) {
      return false;
    }
    for (PsiElement element : elements) {
      if (!(element instanceof PsiDirectory)) return false;
      final PsiDirectory directory = ((PsiDirectory)element);
      if (RefactoringUtil.isSourceRoot(directory)) {
        return false;
      }
      final PsiPackage aPackage = directory.getPackage();
      if (aPackage == null) return false;
      if ("".equals(aPackage.getQualifiedName())) return false;
      final VirtualFile sourceRootForFile = ProjectRootManager.getInstance(element.getProject()).getFileIndex()
        .getSourceRootForFile(directory.getVirtualFile());
      if (sourceRootForFile == null) return false;
    }
    return true;
  }

  public static boolean canMoveDirectories(PsiElement[] elements) {
    if (elements == null) {
      throw new IllegalArgumentException("elements cannot be null");
    }
    for (PsiElement element : elements) {
      if (!(element instanceof PsiDirectory)) {
        return false;
      }
    }

    for (PsiElement element : elements) {
      PsiDirectory directory = (PsiDirectory)element;

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
    for (PsiDirectory subdirectory : subdirectories) {
      if (hasPackages(subdirectory)) {
        return true;
      }
    }
    return false;
  }
}
