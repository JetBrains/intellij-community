/**
 * created at Nov 27, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.ide.util.DirectoryChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;


public class MoveClassesOrPackagesImpl {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesImpl");

  public static void doMove(final Project project,
                            PsiElement[] elements,
                            PsiElement initialTargetElement,
                            final MoveCallback moveCallback) {
    final PsiElement[] psiElements = new PsiElement[elements.length];
    List<VirtualFile> readOnly = new ArrayList<VirtualFile>();
    for (int idx = 0; idx < elements.length; idx++) {
      PsiElement element = elements[idx];
      if (element instanceof PsiDirectory) {
        PsiPackage aPackage = ((PsiDirectory)element).getPackage();
        LOG.assertTrue(aPackage != null);
        if (aPackage.getQualifiedName().length() == 0) { //is default package
          String message = "Move Package refactoring cannot be applied to default package";
          RefactoringMessageUtil.showErrorMessage("Move", message, HelpID.getMoveHelpID(element), project);
          return;
        }
        element = checkMovePackage(project, aPackage, readOnly);
        if (element == null) return;
      }
      else if (element instanceof PsiPackage) {
        element = checkMovePackage(project, (PsiPackage) element, readOnly);
        if (element == null) return;
      }
      else if (element instanceof PsiClass) {
        PsiClass aClass = (PsiClass)element;
        if (aClass instanceof PsiAnonymousClass) {
          String message = "Move Class refactoring cannot be applied to anonymous classes";
          RefactoringMessageUtil.showErrorMessage("Move", message, HelpID.getMoveHelpID(element), project);
          return;
        }
        boolean condition = aClass.getParent() instanceof PsiFile;
        if (!condition) {
          String message =
            "Cannot perform the refactoring.\n" +
            "Moving local classes is not supported.";
          RefactoringMessageUtil.showErrorMessage("Move", message, HelpID.getMoveHelpID(element), project);
          return;
        }
        if (!aClass.isWritable()) {
          readOnly.add(aClass.getContainingFile().getVirtualFile());
        }
      }
      psiElements[idx] = element;
    }

    if (!readOnly.isEmpty()) {
      Messages.showErrorDialog(project, "Cannot perform refactorings.\n Some files or directories are read only.", "Move");
      VirtualFileManager.getInstance().fireReadOnlyModificationAttempt(
        (VirtualFile[])readOnly.toArray(new VirtualFile[readOnly.size()]));
      return;
    }

    final String initialTargetPackageName = getInitialTargetPackageName(initialTargetElement, psiElements);
    final PsiDirectory initialTargetDirectory = getInitialTargetDirectory(initialTargetElement, psiElements);
    final boolean isTargetDirectoryFixed = getContainerDirectory(initialTargetElement) != null;

    final MoveClassesOrPackagesDialog.Callback doRun = new MoveClassesOrPackagesDialog.Callback() {
      public void run(final MoveClassesOrPackagesDialog moveDialog) {
        final RefactoringSettings refactoringSettings = RefactoringSettings.getInstance();
        final boolean searchInComments = moveDialog.isSearchInComments();
        final boolean searchInNonJavaFiles = moveDialog.isSearchInNonJavaFiles();
        refactoringSettings.MOVE_SEARCH_IN_COMMENTS = searchInComments;
        refactoringSettings.MOVE_SEARCH_IN_NONJAVA_FILES = searchInNonJavaFiles;

        final MoveDestination moveDestination = moveDialog.getMoveDestination();

        PsiManager manager = PsiManager.getInstance(project);
        for (int i = 0; i < psiElements.length; i++) {
          final PsiElement element = psiElements[i];
          String message = verifyDestinationForElement(element, moveDestination);
          if (message != null) {
            String helpId = HelpID.getMoveHelpID(psiElements[0]);
            RefactoringMessageUtil.showErrorMessage("Error", message, helpId, project);
            return;
          }
        }
        try {
          for (int idx = 0; idx < psiElements.length; idx++) {
            PsiElement psiElement = psiElements[idx];
            if (psiElement instanceof PsiClass) {
              final PsiDirectory targetDirectory = moveDestination.getTargetIfExists(psiElement.getContainingFile());
              if (targetDirectory != null) {
                manager.checkMove(psiElement, targetDirectory);
              }
            }
          }

          new MoveClassesOrPackagesProcessor(
            project,
            psiElements,
            moveDestination, searchInComments,
            searchInNonJavaFiles,
            moveDialog.isPreviewUsages(),
            moveCallback,
            new Runnable() {
              public void run() {
                moveDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
              }
            }).run(null);
        }
        catch (IncorrectOperationException e) {
          String helpId = HelpID.getMoveHelpID(psiElements[0]);
          RefactoringMessageUtil.showErrorMessage("Error", e.getMessage(), helpId, project);
          return;
        }
      }
    };


    boolean searchInNonJavaEnabled = false;
    for (int i = 0; i < psiElements.length && !searchInNonJavaEnabled; i++) {
      PsiElement psiElement = psiElements[i];
      searchInNonJavaEnabled = RefactoringUtil.isSearchInNonJavaEnabled(psiElement);
    }
    final MoveClassesOrPackagesDialog moveDialog = new MoveClassesOrPackagesDialog(project, doRun,
                                                                                   searchInNonJavaEnabled);
    boolean searchInComments = RefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS;
    boolean searchInNonJavaFiles = RefactoringSettings.getInstance().MOVE_SEARCH_IN_NONJAVA_FILES;
    moveDialog.setData(
      psiElements,
      initialTargetPackageName,
      initialTargetDirectory,
      isTargetDirectoryFixed, searchInComments,
      searchInNonJavaFiles,
      HelpID.getMoveHelpID(psiElements[0])
    );
    moveDialog.show();
  }

  private static String verifyDestinationForElement(final PsiElement element, final MoveDestination moveDestination) {
    final String message;
    if (element instanceof PsiDirectory) {
      message = moveDestination.verify((PsiDirectory) element);
    }
    else if (element instanceof PsiPackage) {
      message = moveDestination.verify((PsiPackage) element);
    }
    else {
      message = moveDestination.verify(element.getContainingFile());
    }
    return message;
  }

  private static PsiElement checkMovePackage(Project project, PsiPackage aPackage, List<VirtualFile> readOnly) {
    PsiElement element;
    final PsiDirectory[] directories = aPackage.getDirectories();
    final VirtualFile[] virtualFiles = aPackage.occursInPackagePrefixes();
    if (directories.length > 1 || virtualFiles.length > 0) {
      final StringBuffer message = new StringBuffer();
      RenameUtil.buildPackagePrefixChangedMessage(virtualFiles, message, aPackage.getQualifiedName());
      if (directories.length > 1) {
        RenameUtil.buildMultipleDirectoriesInPackageMessage(message, aPackage, directories);
        message.append("\n\nAll these directories will be moved and all references to \n");
        message.append(aPackage.getQualifiedName());
        message.append("\nwill be changed.");
      }
      message.append("\nDo you wish to continue?");
      int ret = Messages.showYesNoDialog(project, message.toString(), "Warning", Messages.getWarningIcon());
      if (ret != 0) {
        return null;
      }
    }
    checkMove(aPackage, readOnly);
    element = aPackage;
    return element;
  }

  private static String getInitialTargetPackageName(PsiElement initialTargetElement, final PsiElement[] movedElements) {
    String name = getContainerPackageName(initialTargetElement);
    if (name == null) {
      if (movedElements != null) {
        name = getTargetPackageNameForMovedElement(movedElements[0]);
      }
      if (name == null) {
        final PsiDirectory commonDirectory = getCommonDirectory(movedElements);
        if (commonDirectory != null && commonDirectory.getPackage() != null) {
          name = commonDirectory.getPackage().getQualifiedName();
        }
      }
    }
    if (name == null) {
      name = "";
    }
    return name;
  }

  private static PsiDirectory getCommonDirectory(PsiElement[] movedElements) {
    PsiDirectory commonDirectory = null;

    for (int i = 0; i < movedElements.length; i++) {
      PsiElement movedElement = movedElements[i];
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
    if (commonDirectory != null) {
      return commonDirectory;
    }
    else {
      return null;
    }
  }

  private static String getContainerPackageName(final PsiElement psiElement) {
    if (psiElement instanceof PsiPackage) {
      return ((PsiPackage)psiElement).getQualifiedName();
    }
    else if (psiElement instanceof PsiDirectory) {
      PsiPackage aPackage = ((PsiDirectory)psiElement).getPackage();
      return (aPackage != null) ? aPackage.getQualifiedName() : "";
    }
    else if (psiElement != null) {
      PsiPackage aPackage = psiElement.getContainingFile().getContainingDirectory().getPackage();
      return (aPackage != null) ? aPackage.getQualifiedName() : "";
    }
    else {
      return null;
    }
  }

  private static String getTargetPackageNameForMovedElement(final PsiElement psiElement) {
    if (psiElement instanceof PsiPackage) {
      final PsiPackage psiPackage = ((PsiPackage)psiElement);
      final PsiPackage parentPackage = psiPackage.getParentPackage();
      return parentPackage != null ? parentPackage.getQualifiedName() : "";
    }
    else if (psiElement instanceof PsiDirectory) {
      PsiPackage aPackage = ((PsiDirectory)psiElement).getPackage();
      return (aPackage != null) ? getTargetPackageNameForMovedElement(aPackage) : "";
    }
    else if (psiElement != null) {
      PsiPackage aPackage = psiElement.getContainingFile().getContainingDirectory().getPackage();
      return (aPackage != null) ? aPackage.getQualifiedName() : "";
    }
    else {
      return null;
    }
  }


  private static PsiDirectory getInitialTargetDirectory(PsiElement initialTargetElement,
                                                        final PsiElement[] movedElements) {
    PsiDirectory initialTargetDirectory = getContainerDirectory(initialTargetElement);
    if (initialTargetDirectory == null) {
      if (movedElements != null) {
        final PsiDirectory commonDirectory = getCommonDirectory(movedElements);
        if (commonDirectory != null) {
          initialTargetDirectory = commonDirectory;
        }
        else {
          initialTargetDirectory = getContainerDirectory(movedElements[0]);
        }
      }
    }
    return initialTargetDirectory;
  }

  private static void checkMove(PsiElement elementToMove, List<VirtualFile> readOnly) {
    if (elementToMove instanceof PsiPackage) {
      final PsiDirectory[] directories = ((PsiPackage)elementToMove).getDirectories();
      for (int i = 0; i < directories.length; i++) {
        PsiDirectory directory = directories[i];
        checkMove(directory, readOnly);
      }
    } else if (elementToMove instanceof PsiDirectory) {
      final PsiFile[] files = ((PsiDirectory)elementToMove).getFiles();
      if (!elementToMove.isWritable()) {
        readOnly.add(((PsiDirectory)elementToMove).getVirtualFile());
        return;
      }
      for (int i = 0; i < files.length; i++) {
        PsiFile file = files[i];
        checkMove(file, readOnly);
      }
      final PsiDirectory[] subdirectories = ((PsiDirectory)elementToMove).getSubdirectories();
      for (int i = 0; i < subdirectories.length; i++) {
        PsiDirectory subdirectory = subdirectories[i];
        checkMove(subdirectory, readOnly);
      }
    } else if (elementToMove instanceof PsiFile) {
      if (!elementToMove.isWritable()) {
        readOnly.add(((PsiFile)elementToMove).getVirtualFile());
        return;
      }
    }
  }

  private static PsiDirectory getContainerDirectory(final PsiElement psiElement) {
    if (psiElement instanceof PsiPackage) {
      return null; //??
    }
    else if (psiElement instanceof PsiDirectory) {
      return (PsiDirectory)psiElement;
    }
    else if (psiElement != null) {
      return psiElement.getContainingFile().getContainingDirectory();
    }
    else {
      return null;
    }
  }

  public static void doRearrangePackage(final Project project, final PsiDirectory[] directories) {
    final ArrayList<VirtualFile> readOnly = new ArrayList<VirtualFile>();
    for (int i = 0; i < directories.length; i++) {
      PsiDirectory directory = directories[i];
      checkMove(directory, readOnly);
    }
    if (!readOnly.isEmpty()) {
      Messages.showErrorDialog(project, "Cannot perform refactorings.\n Some files or directories are read only.", "Move");
      VirtualFileManager.getInstance().fireReadOnlyModificationAttempt(
        (VirtualFile[])readOnly.toArray(new VirtualFile[readOnly.size()]));
      return;
    }
    List<PsiDirectory> sourceRootDirectories = buildRearrangeTargetsList(project, directories);
    DirectoryChooser chooser = new DirectoryChooser(project);
    chooser.setTitle("Select source root");
    chooser.fillList((PsiDirectory[])sourceRootDirectories.toArray(new PsiDirectory[sourceRootDirectories.size()]), null, project, "");
    chooser.show();
    if (!chooser.isOK()) return;
    final PsiDirectory selectedTarget = chooser.getSelectedDirectory();
    if (selectedTarget == null) return;
    final Ref<IncorrectOperationException> ex = Ref.create(null);
    final String commandDescription = "Moving directories";
    Runnable runnable = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final LvcsAction lvcsAction = LvcsIntegration.checkinFilesBeforeRefactoring(project, commandDescription);
            try {
              rearrangeDirectoriesToTarget(directories, selectedTarget);
            }
            catch (IncorrectOperationException e) {
              ex.set(e);
            }
            finally{
              LvcsIntegration.checkinFilesAfterRefactoring(project, lvcsAction);
            }
          }
        });
      }
    };
    CommandProcessor.getInstance().executeCommand(project, runnable, commandDescription, null);
    if (ex.get() != null) {
      RefactoringUtil.processIncorrectOperation(project, ex.get());
    }
  }

  private static List<PsiDirectory> buildRearrangeTargetsList(final Project project, final PsiDirectory[] directories) {
    final VirtualFile[] sourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
    List<PsiDirectory> sourceRootDirectories = new ArrayList<PsiDirectory>();
    sourceRoots:
    for (int i = 0; i < sourceRoots.length; i++) {
      final VirtualFile sourceRoot = sourceRoots[i];
      PsiDirectory sourceRootDirectory = PsiManager.getInstance(project).findDirectory(sourceRoot);
      if (sourceRootDirectory == null) {
        LOG.error("Cannot find PsiDirectory for: " + sourceRoot.getPresentableUrl());
        continue sourceRoots;
      }
      final PsiPackage aPackage = sourceRootDirectory.getPackage();
      if (aPackage == null) continue;
      final String packagePrefix = aPackage.getQualifiedName();
      for (int j = 0; j < directories.length; j++) {
        final PsiDirectory directory = directories[j];
        String qualifiedName = directory.getPackage().getQualifiedName();
        if (!qualifiedName.startsWith(packagePrefix)) {
          continue sourceRoots;
        }
      }
      sourceRootDirectories.add(sourceRootDirectory);
    }
    return sourceRootDirectories;
  }

  private static void rearrangeDirectoriesToTarget(PsiDirectory[] directories, PsiDirectory selectedTarget)
    throws IncorrectOperationException {
    final VirtualFile sourceRoot = selectedTarget.getVirtualFile();
    for (int i = 0; i < directories.length; i++) {
      PsiDirectory directory = directories[i];
      final PsiPackage parentPackage = directory.getPackage().getParentPackage();
      final PackageWrapper wrapper = new PackageWrapper(parentPackage);
      final PsiDirectory moveTarget = RefactoringUtil.createPackageDirectoryInSourceRoot(wrapper, sourceRoot);
      MoveClassesOrPackagesUtil.moveDirectoryRecursively(directory, moveTarget);
    }
  }

}
