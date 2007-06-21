/**
 * created at Oct 25, 2001
 * @author Jeka
 */
package com.intellij.refactoring.extractSuperclass;

import com.intellij.localvcs.integration.LocalHistoryAction;
import com.intellij.localvcs.integration.LocalHistory;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractInterface.ExtractClassUtil;
import com.intellij.refactoring.memberPullUp.PullUpConflictsUtil;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ExtractSuperclassHandler implements RefactoringActionHandler, ExtractSuperclassDialog.Callback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.extractSuperclass.ExtractSuperclassHandler");

  public static final String REFACTORING_NAME = RefactoringBundle.message("extract.superclass.title");

  private PsiClass mySubclass;
  private Project myProject;


  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.class"));
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.EXTRACT_SUPERCLASS, project);
        return;
      }
      if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  public void invoke(@NotNull final Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    myProject = project;
    mySubclass = (PsiClass)elements[0];

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, mySubclass)) return;

    if (mySubclass.isInterface()) {
      String message =
        RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("superclass.cannot.be.extracted.from.an.interface"));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.EXTRACT_SUPERCLASS, project);
      return;
    }

    if (mySubclass.isEnum()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("superclass.cannot.be.extracted.from.an.enum"));
      CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.EXTRACT_SUPERCLASS, project);
      return;
    }


    final MemberInfo[] memberInfos = MemberInfo.extractClassMembers(mySubclass, new MemberInfo.Filter() {
      public boolean includeMember(PsiMember element) {
        return true;
      }
    }, false);
    final String targetPackageName =
      (mySubclass.getContainingFile() instanceof PsiJavaFile) ? ((PsiJavaFile)mySubclass.getContainingFile()).getPackageName() : null;


    final ExtractSuperclassDialog dialog =
      new ExtractSuperclassDialog(project, mySubclass, memberInfos, targetPackageName, ExtractSuperclassHandler.this);
    dialog.show();
    if (!dialog.isOK() || !dialog.isExtractSuperclass()) return;

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            doRefactoring(project, mySubclass, dialog);
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }, REFACTORING_NAME, null);

  }

  public boolean checkConflicts(ExtractSuperclassDialog dialog) {
    final MemberInfo[] infos = dialog.getSelectedMemberInfos();
    final PsiDirectory targetDirectory = dialog.getTargetDirectory();
    final PsiPackage targetPackage;
    if (targetDirectory != null) {
      targetPackage = targetDirectory.getPackage();
    }
    else {
      targetPackage = null;
    }
    String[] conflicts =
      PullUpConflictsUtil.checkConflicts(infos, mySubclass, null, targetPackage, targetDirectory, dialog.getContainmentVerifier());
    if (conflicts.length > 0) {
      ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
      conflictsDialog.show();
      return conflictsDialog.isOK();
    }
    return true;
  }

  // invoked inside Command and Atomic action
  private void doRefactoring(final Project project, final PsiClass subclass, final ExtractSuperclassDialog dialog) {
    final String superclassName = dialog.getSuperclassName();
    final PsiDirectory targetDirectory = dialog.getTargetDirectory();
    final MemberInfo[] selectedMemberInfos = dialog.getSelectedMemberInfos();
    final JavaDocPolicy javaDocPolicy = new JavaDocPolicy(dialog.getJavaDocPolicy());
    LocalHistoryAction a = LocalHistory.startAction(myProject, getCommandName(subclass, superclassName));
    try {
      PsiClass superclass = null;

      try {
        superclass =
          ExtractSuperClassUtil.extractSuperClass(project, targetDirectory, superclassName, subclass, selectedMemberInfos, javaDocPolicy);
      }
      finally {
        a.finish();
      }

      // ask whether to search references to subclass and turn them into refs to superclass if possible
      if (superclass != null) {
        ExtractClassUtil.askAndTurnRefsToSuper(project, subclass, superclass);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

  }

  private String getCommandName(final PsiClass subclass, String newName) {
    return RefactoringBundle.message("extract.superclass.command.name", newName, UsageViewUtil.getDescriptiveName(subclass));
  }

}
