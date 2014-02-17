package com.jetbrains.python.refactoring.classes.pushDown;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.classMembers.UsedByDependencyMemberInfoModel;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedPresenterWithPreviewImpl;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author Ilya.Kazakevich
 */
public class PyPushDownPresenterImpl extends MembersBasedPresenterWithPreviewImpl<PyPushDownView> implements PyPushDownPresenter {
  @NotNull
  private final Project myProject;

  public PyPushDownPresenterImpl(@NotNull final Project project,
                                 @NotNull final PyPushDownView view,
                                 @NotNull final PyClass classUnderRefactoring,
                                 @NotNull final PyMemberInfoStorage infoStorage) {
    super(view, classUnderRefactoring, infoStorage);
    myProject = project;
  }

  @NotNull
  @Override
  public BaseRefactoringProcessor createProcessor() {
    return new PyPushDownProcessor(myProject, myView.getSelectedMemberInfos(), myClassUnderRefactoring);
  }

  @NotNull
  @Override
  protected MultiMap<PsiElement, String> getConflicts() {
    return new PyPushDownConflicts(myClassUnderRefactoring, myStorage.getClassMemberInfos(myClassUnderRefactoring)).getConflicts();
  }

  @Override
  public void launch() {
    UsedByDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo<PyElement>> model =
      new UsedByDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo<PyElement>>(myClassUnderRefactoring);
    myView
      .configure(new MembersViewInitializationInfo(model, PyUtil.filterOutObject(myStorage.getClassMemberInfos(myClassUnderRefactoring))));
    myView.initAndShow();
  }
}
