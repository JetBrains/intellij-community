package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MoveFilesOrDirectoriesProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor");

  private PsiElement[] myElementsToMove;
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;
  private PsiDirectory myNewParent;
  private MoveCallback myMoveCallback;

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

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new MoveFilesOrDirectoriesViewDescriptor(myElementsToMove, mySearchInComments, mySearchInNonJavaFiles, myNewParent);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    List<UsageInfo> result = new ArrayList<UsageInfo>();
    for (int i = 0; i < myElementsToMove.length; i++) {
      PsiElement element = myElementsToMove[i];
      for (PsiReference reference : ReferencesSearch.search(element).findAll()) {
        result.add(new MyUsageInfo(reference.getElement(), i, reference));
      }
    }

    return result.toArray(new UsageInfo[result.size()]);
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    prepareSuccessful();
    return true;
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == myElementsToMove.length);
    System.arraycopy(elements, 0, myElementsToMove, 0, elements.length);
  }

  protected void performRefactoring(UsageInfo[] usages) {
    // If files are being moved then I need to collect some information to delete these
    // filese from CVS. I need to know all common parents of the moved files and releative
    // paths.

    // Move files with correction of references.

    try {
      for (final PsiElement element : myElementsToMove) {

        if (element instanceof PsiDirectory) {
          MoveFilesOrDirectoriesUtil.doMoveDirectory((PsiDirectory)element, myNewParent);
        }
        else if (element instanceof PsiFile) {
          MoveFilesOrDirectoriesUtil.doMoveFile((PsiFile)element, myNewParent);
        }

        final RefactoringElementListener elementListener = getTransaction().getElementListener(element);
        elementListener.elementMoved(element);
      }

      for (UsageInfo usageInfo : usages) {
        final MyUsageInfo info = (MyUsageInfo)usageInfo;
        final PsiElement element = myElementsToMove[info.myIndex];
        final PsiElement usageElement = info.getElement();
        if (usageElement != null) {
          final PsiFile usageFile = usageElement.getContainingFile();
          final PsiFile psiFile = usageFile.getViewProvider().getPsi(usageFile.getViewProvider().getBaseLanguage());
          if (psiFile != null && psiFile.equals(element)) {
            continue;  // already processed in MoveFilesOrDirectoriesUtil.doMoveFile
          }
        }
        info.myReference.bindToElement(element);
      }

      // Perform CVS "add", "remove" commands on moved files.

      if (myMoveCallback != null) {
        myMoveCallback.refactoringCompleted();
      }

    }
    catch (IncorrectOperationException e) {
      @NonNls final String message = e.getMessage();
      final int index = message != null ? message.indexOf("java.io.IOException") : -1;
      if (index >= 0) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                Messages.showMessageDialog(myProject, message.substring(index + "java.io.IOException".length()),
                                           RefactoringBundle.message("error.title"),
                                           Messages.getErrorIcon());
              }
            });
      }
      else {
        LOG.error(e);
      }
    }
  }

  protected String getCommandName() {
    return RefactoringBundle.message("move.tltle"); //TODO!!
  }

  static class MyUsageInfo extends UsageInfo {
    int myIndex;
    PsiReference myReference;

    public MyUsageInfo(PsiElement element, final int index, PsiReference reference) {
      super(element);
      myIndex = index;
      myReference = reference;
    }
  }
}
