/**
 * created at Nov 27, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.ide.util.DirectoryChooser;
import com.intellij.localvcs.integration.LocalHistoryAction;
import com.intellij.localvcs.integration.LocalHistory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MoveClassesOrPackagesImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesImpl");

  public static void doMove(final Project project,
                            PsiElement[] elements,
                            PsiElement initialTargetElement,
                            final MoveCallback moveCallback) {
    final PsiElement[] psiElements = adjustForMove(project, elements, initialTargetElement);
    if (psiElements == null) {
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(psiElements), true)) {
      return;
    }

    final String initialTargetPackageName = getInitialTargetPackageName(initialTargetElement, psiElements);
    final PsiDirectory initialTargetDirectory = getInitialTargetDirectory(initialTargetElement, psiElements);
    final boolean isTargetDirectoryFixed = getContainerDirectory(initialTargetElement) != null;

    boolean searchTextOccurences = false;
    for (int i = 0; i < psiElements.length && !searchTextOccurences; i++) {
      PsiElement psiElement = psiElements[i];
      searchTextOccurences = RefactoringUtil.isSearchTextOccurencesEnabled(psiElement);
    }
    final MoveClassesOrPackagesDialog moveDialog =
      new MoveClassesOrPackagesDialog(project, searchTextOccurences, psiElements, moveCallback);
    boolean searchInComments = RefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS;
    boolean searchForTextOccurences = RefactoringSettings.getInstance().MOVE_SEARCH_FOR_TEXT;
    moveDialog.setData(psiElements, initialTargetPackageName, initialTargetDirectory, isTargetDirectoryFixed, searchInComments,
                       searchForTextOccurences, HelpID.getMoveHelpID(psiElements[0]));
    moveDialog.show();
  }

  @Nullable
  public static PsiElement[] adjustForMove(final Project project, final PsiElement[] elements, final PsiElement targetElement) {
    final PsiElement[] psiElements = new PsiElement[elements.length];
    List<String> names = new ArrayList<String>();
    for (int idx = 0; idx < elements.length; idx++) {
      PsiElement element = elements[idx];
      if (element instanceof PsiDirectory) {
        PsiPackage aPackage = ((PsiDirectory)element).getPackage();
        LOG.assertTrue(aPackage != null);
        if (aPackage.getQualifiedName().length() == 0) { //is default package
          String message = RefactoringBundle.message("move.package.refactoring.cannot.be.applied.to.default.package");
          CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.tltle"), message, HelpID.getMoveHelpID(element), project);
          return null;
        }
        if (!checkNesting(project, aPackage, targetElement)) return null;
        if (!checkMovePackage(project, aPackage)) return null;
        element = aPackage;
      }
      else if (element instanceof PsiPackage) {
        final PsiPackage psiPackage = (PsiPackage)element;
        if (!checkNesting(project, psiPackage, targetElement)) return null;
        if (!checkMovePackage(project, psiPackage)) return null;
      }
      else if (element instanceof PsiClass) {
        PsiClass aClass = (PsiClass)element;
        if (aClass instanceof PsiAnonymousClass) {
          String message = RefactoringBundle.message("move.class.refactoring.cannot.be.applied.to.anonymous.classes");
          CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.tltle"), message, HelpID.getMoveHelpID(element), project);
          return null;
        }
        if (!(aClass.getParent() instanceof PsiFile)) {
          String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("moving.local.classes.is.not.supported"));
          CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.tltle"), message, HelpID.getMoveHelpID(element), project);
          return null;
        }

        final PsiFile file = aClass.getContainingFile();
        String name = file instanceof PsiJavaFile && ((PsiJavaFile)file).getClasses().length > 1
                      ? aClass.getName() + "." + StdFileTypes.JAVA.getDefaultExtension()
                      : file.getName();
        if (names.contains(name)) {
          String message = RefactoringBundle
            .getCannotRefactorMessage(RefactoringBundle.message("there.are.going.to.be.multiple.destination.files.with.the.same.name"));
          CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.tltle"), message, HelpID.getMoveHelpID(element), project);
          return null;
        }

        names.add(name);
      }
      psiElements[idx] = element;
    }

    return psiElements;
  }

  private static boolean checkMovePackage(Project project, PsiPackage aPackage) {
    final PsiDirectory[] directories = aPackage.getDirectories();
    final VirtualFile[] virtualFiles = aPackage.occursInPackagePrefixes();
    if (directories.length > 1 || virtualFiles.length > 0) {
      final StringBuffer message = new StringBuffer();
      RenameUtil.buildPackagePrefixChangedMessage(virtualFiles, message, aPackage.getQualifiedName());
      if (directories.length > 1) {
        RenameUtil.buildMultipleDirectoriesInPackageMessage(message, aPackage, directories);
        message.append("\n\n");
        String report = RefactoringBundle
          .message("all.these.directories.will.be.moved.and.all.references.to.0.will.be.changed", aPackage.getQualifiedName());
        message.append(report);
      }
      message.append("\n");
      message.append(RefactoringBundle.message("do.you.wish.to.continue"));
      int ret =
        Messages.showYesNoDialog(project, message.toString(), RefactoringBundle.message("warning.title"), Messages.getWarningIcon());
      if (ret != 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean checkNesting(final Project project, final PsiPackage srcPackage, final PsiElement targetElement) {
    final PsiPackage targetPackage = targetElement instanceof PsiPackage
                                     ? (PsiPackage)targetElement
                                     : targetElement instanceof PsiDirectory ? ((PsiDirectory)targetElement).getPackage() : null;
    for (PsiPackage curPackage = targetPackage; curPackage != null; curPackage = curPackage.getParentPackage()) {
      if (curPackage.equals(srcPackage)) {
        CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("move.tltle"),
                                               RefactoringBundle.message("cannot.move.package.into.itself"),
                                               HelpID.getMoveHelpID(srcPackage), project);
        return false;
      }
    }
    return true;
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

  @Nullable
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


  private static PsiDirectory getInitialTargetDirectory(PsiElement initialTargetElement, final PsiElement[] movedElements) {
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

  @Nullable
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

    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(directories), true)) {
      return;
    }

    List<PsiDirectory> sourceRootDirectories = buildRearrangeTargetsList(project, directories);
    DirectoryChooser chooser = new DirectoryChooser(project);
    chooser.setTitle(RefactoringBundle.message("select.source.root.chooser.title"));
    chooser.fillList(sourceRootDirectories.toArray(new PsiDirectory[sourceRootDirectories.size()]), null, project, "");
    chooser.show();
    if (!chooser.isOK()) return;
    final PsiDirectory selectedTarget = chooser.getSelectedDirectory();
    if (selectedTarget == null) return;
    final Ref<IncorrectOperationException> ex = Ref.create(null);
    final String commandDescription = RefactoringBundle.message("moving.directories.command");
    Runnable runnable = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            LocalHistoryAction a = LocalHistory.startAction(project, commandDescription);
            try {
              rearrangeDirectoriesToTarget(directories, selectedTarget);
            }
            catch (IncorrectOperationException e) {
              ex.set(e);
            }
            finally {
              a.finish();
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
    for (final VirtualFile sourceRoot : sourceRoots) {
      PsiDirectory sourceRootDirectory = PsiManager.getInstance(project).findDirectory(sourceRoot);
      if (sourceRootDirectory == null) {
        LOG.error("Cannot find PsiDirectory for: " + sourceRoot.getPresentableUrl());
        continue sourceRoots;
      }
      final PsiPackage aPackage = sourceRootDirectory.getPackage();
      if (aPackage == null) continue;
      final String packagePrefix = aPackage.getQualifiedName();
      for (final PsiDirectory directory : directories) {
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
    for (PsiDirectory directory : directories) {
      final PsiPackage parentPackage = directory.getPackage().getParentPackage();
      final PackageWrapper wrapper = new PackageWrapper(parentPackage);
      final PsiDirectory moveTarget = RefactoringUtil.createPackageDirectoryInSourceRoot(wrapper, sourceRoot);
      MoveClassesOrPackagesUtil.moveDirectoryRecursively(directory, moveTarget);
    }
  }

}
