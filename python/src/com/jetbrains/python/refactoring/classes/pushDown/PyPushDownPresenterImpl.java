package com.jetbrains.python.refactoring.classes.pushDown;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.classMembers.UsedByDependencyMemberInfoModel;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.search.PyClassInheritorsSearch;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedPresenterWithPreviewImpl;
import com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Created by Ilya.Kazakevich on 10.02.14.
 */
public class PyPushDownPresenterImpl extends MembersBasedPresenterWithPreviewImpl<PyPushDownView> implements PyPushDownPresenter {
  public PyPushDownPresenterImpl(@NotNull PyPushDownView view,
                                 @NotNull PyClass classUnderRefactoring,
                                 @NotNull PyMemberInfoStorage infoStorage) {
    super(view, classUnderRefactoring, infoStorage);
  }

  @NotNull
  @Override
  public BaseRefactoringProcessor createProcessor() {
    return new PyPushDownProcessor(myView.getSelectedMemberInfos(), myClassUnderRefactoring);
  }

  @NotNull
  @Override
  protected MultiMap<PsiElement, String> getConflicts() {
    return new PyPushDownConflicts(myClassUnderRefactoring, myStorage.getClassMemberInfos(myClassUnderRefactoring)).getConflicts();
  }

  @Override
  public void launch() {
    UsedByDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo> model =
      new UsedByDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo>(myClassUnderRefactoring);
    myView.configure(new MembersViewInitializationInfo(model, PyUtil.filterOutObject(myStorage.getClassMemberInfos(myClassUnderRefactoring))));
    myView.initAndShow();
  }
}
