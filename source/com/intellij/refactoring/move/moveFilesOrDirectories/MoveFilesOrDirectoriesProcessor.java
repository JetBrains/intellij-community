package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;

public class MoveFilesOrDirectoriesProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor");

  private PsiElement[] myElementsToMove;
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;
  private PsiDirectory myNewParent;
  private MoveCallback myMoveCallback;
  private UsageInfo[] myUsagesAfterRefactoring;

  public MoveFilesOrDirectoriesProcessor(
    Project project,
    PsiElement[] elements,
    PsiDirectory newParent,
    boolean searchInComments,
    boolean searchInNonJavaFiles,
    MoveCallback moveCallback,
    Runnable prepareSuccessfulCallback) {
    super(project, prepareSuccessfulCallback);
    myElementsToMove = elements;
    myNewParent = newParent;
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
    myMoveCallback = moveCallback;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    PsiElement[] elements = new PsiElement[myElementsToMove.length];
    for (int idx = 0; idx < myElementsToMove.length; idx++) {
      elements[idx] = myElementsToMove[idx];
    }
    return new MoveFilesOrDirectoriesViewDescriptor(elements, mySearchInComments, mySearchInNonJavaFiles, myNewParent,
                                                    usages, refreshCommand);
  }

  protected UsageInfo[] findUsages() {
    return new UsageInfo[0];
  }

  protected boolean preprocessUsages(UsageInfo[][] u) {
    final UsageInfo[] usages = u[0];
    ArrayList filteredUsages = new ArrayList();
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      filteredUsages.add(usage);
    }

    u[0] = (UsageInfo[])filteredUsages.toArray(new UsageInfo[filteredUsages.size()]);
    prepareSuccessful();
    return true;
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == myElementsToMove.length);
    for (int idx = 0; idx < elements.length; idx++) {
      myElementsToMove[idx] = elements[idx];
    }
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    return false;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    ArrayList arrayList = new ArrayList();
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      arrayList.add(usage);
    }

    // If files are being moved then I need to collect some information to delete these
    // filese from CVS. I need to know all common parents of the moved files and releative
    // paths.

    // Move files with correction of references.

    try {
      for (int idx = 0; idx < myElementsToMove.length; idx++) {
        PsiElement element = myElementsToMove[idx];
        final RefactoringElementListener elementListener = getTransaction().getElementListener(element);
        if (element instanceof PsiDirectory) {

          element = MoveFilesOrDirectoriesUtil.doMoveDirectory((PsiDirectory)element, myNewParent);

        }
        else if (element instanceof PsiFile) {
          element = MoveFilesOrDirectoriesUtil.doMoveFile((PsiFile)element, myNewParent);
        }

        elementListener.elementMoved(element);
        myElementsToMove[idx] = element;
      }

      myUsagesAfterRefactoring = usages;

      // Perform CVS "add", "remove" commands on moved files.

      if (myMoveCallback != null) {
        myMoveCallback.refactoringCompleted();
      }

    }
    catch (IncorrectOperationException e) {
      final String message = e.getMessage();
      final int index = (message != null) ? message.indexOf("java.io.IOException") : -1;
      if (index >= 0) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                Messages.showMessageDialog(myProject, message.substring(index + "java.io.IOException".length()), "Error",
                                           Messages.getErrorIcon());
              }
            });
      }
      else {
        LOG.error(e);
      }
    }
  }

  protected void performPsiSpoilingRefactoring() {
    if (myUsagesAfterRefactoring != null) {
      RefactoringUtil.renameNonCodeUsages(myProject, myUsagesAfterRefactoring);
    }
  }

  protected String getCommandName() {
    return "Move"; //TODO!!
  }

}
