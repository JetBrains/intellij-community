/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 18.06.2002
 * Time: 12:45:30
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.memberPullUp;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

public class PullUpHandler implements RefactoringActionHandler, PullUpDialog.Callback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.memberPullUp.PullUpHandler");
  public static final String REFACTORING_NAME = "Pull Members Up";
  private PsiClass mySubclass;
  private Project myProject;

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);

    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message =
                "Cannot perform the refactoring.\n" +
                "The caret should be positioned inside a class to pull members from.";
        RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, null/*HelpID.MEMBERS_PULL_UP*/, project);
        return;
      }

      if (!element.isWritable()) {
        RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(project, element);
        return;
      }

      if (element instanceof PsiClass || element instanceof PsiField || element instanceof PsiMethod) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  public void invoke(final Project project, PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;
    myProject = project;

    PsiElement element = elements[0];
    PsiClass aClass;
    PsiElement aMember = null;

    if (element instanceof PsiClass) {
      aClass = (PsiClass) element;
    } else if (element instanceof PsiMethod) {
      aClass = ((PsiMethod) element).getContainingClass();
      aMember = element;
    } else if (element instanceof PsiField) {
      aClass = ((PsiField) element).getContainingClass();
      aMember = element;
    } else return;

    if(aClass == null) {
      String message =
              "Cannot perform the refactoring.\n" +
              "Refactoring is not supported in current context.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, null/*HelpID.MEMBERS_PULL_UP*/, project);
      return;
    }

    ArrayList bases = RefactoringHierarchyUtil.createBasesList(aClass, false, true);

    if (bases.isEmpty()) {
      String message =
              "Cannot perform the refactoring.\n" +
              aClass.getQualifiedName() + " does not have base classes/interfaces in current project.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, null/*HelpID.MEMBERS_PULL_UP*/, project);
      return;
    }


    mySubclass = aClass;
    MemberInfoStorage memberInfoStorage = new MemberInfoStorage(mySubclass, new MemberInfo.Filter() {
      public boolean includeMember(PsiMember element) {
        if (element instanceof PsiMethod) {
          return !((PsiMethod) element).isConstructor();
        } else {
          return true;
        }
      }
    });
    List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(mySubclass);
    PsiManager manager = mySubclass.getManager();

    for (int i = 0; i < members.size(); i++) {
      MemberInfo member = members.get(i);

      if (manager.areElementsEquivalent(member.getMember(), aMember)) {
        member.setChecked(true);
        break;
      }
    }

    final PullUpDialog dialog = new PullUpDialog(project, aClass, bases, memberInfoStorage, this);


    dialog.show();

    if (!dialog.isOK()) return;

    CommandProcessor.getInstance().executeCommand(
        myProject, new Runnable() {
              public void run() {
                final Runnable action = new Runnable() {
                  public void run() {
                    doRefactoring(dialog);
                  }
                };
                ApplicationManager.getApplication().runWriteAction(action);
              }
            },
            REFACTORING_NAME,
            null
    );

  }


  private void doRefactoring(PullUpDialog dialog) {
    com.intellij.openapi.localVcs.LvcsAction action = LvcsIntegration.checkinFilesBeforeRefactoring(myProject, getCommandName());
    try {
      try {
        PullUpHelper helper = new PullUpHelper(mySubclass,
                dialog.getSuperClass(),
                dialog.getSelectedMemberInfos(),
                new JavaDocPolicy(dialog.getJavaDocPolicy())
        );
        helper.moveMembersToBase();
        helper.moveFieldInitializations();
      } finally {
        LvcsIntegration.checkinFilesAfterRefactoring(myProject, action);
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private String getCommandName() {
    return "Pulling members up from " + UsageViewUtil.getDescriptiveName(mySubclass);
  }

  public boolean checkConflicts(PullUpDialog dialog) {
    final MemberInfo[] infos = dialog.getSelectedMemberInfos();
    PsiClass superClass = dialog.getSuperClass();
    if (!checkWritable(superClass, infos)) return false;
    String[] conflicts = PullUpConflictsUtil.checkConflicts(infos, mySubclass, superClass, null, null, dialog.getContainmentVerifier());
    if (conflicts.length > 0) {
      ConflictsDialog conflictsDialog = new ConflictsDialog(conflicts, myProject);
      conflictsDialog.show();
      return conflictsDialog.isOK();
    }
    return true;
  }

  private boolean checkWritable(PsiClass superClass, MemberInfo[] infos) {
    if (!superClass.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(myProject, superClass);
      return false;
    }
    for (int i = 0; i < infos.length; i++) {
      MemberInfo info = infos[i];

      if (info.getMember() instanceof  PsiClass && info.getOverrides() != null) continue;
      if (!info.getMember().isWritable()) {
        RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(myProject, info.getMember());
        return false;
      }
    }
    return true;
  }
}
