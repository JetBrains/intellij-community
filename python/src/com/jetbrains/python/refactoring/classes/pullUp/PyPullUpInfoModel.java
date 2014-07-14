package com.jetbrains.python.refactoring.classes.pullUp;

import com.intellij.refactoring.classMembers.AbstractUsesDependencyMemberInfoModel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Dependencies model for PyPullUp refactoring
* @author Ilya.Kazakevich
*/
class PyPullUpInfoModel extends AbstractUsesDependencyMemberInfoModel<PyElement, PyClass, PyMemberInfo<PyElement>> {
  @NotNull
  private final PyPullUpView myView;


  PyPullUpInfoModel(@NotNull final PyClass classUnderRefactoring,
                    @NotNull final PyPullUpView view) {
    super(classUnderRefactoring, null, false);
    myView = view;
  }

  @Override
  public boolean isAbstractEnabled(final PyMemberInfo<PyElement> member) {
    return member.isCouldBeAbstract() && isMemberEnabled(member); // TODO: copy paste with other models, get rid of
  }

  @Override
  public int checkForProblems(@NotNull final PyMemberInfo<PyElement> member) {
    return member.isChecked() ? OK : super.checkForProblems(member);
  }


  @Override
  protected int doCheck(@NotNull final PyMemberInfo<PyElement> memberInfo, final int problem) {
    return problem;
  }

  @Override
  public boolean isMemberEnabled(final PyMemberInfo<PyElement> member) {
    final PyClass currentSuperClass = myView.getSelectedParent();
    if (member.getMember() instanceof PyClass) {
      //TODO: Delegate to Memebers Managers
      final PyClass memberClass = (PyClass)member.getMember();
      if (memberClass.isSubclass(currentSuperClass) || currentSuperClass.isSubclass(memberClass)) {
        return false; //Class is already parent of superclass
      }
    }
    return true;
  }
}
