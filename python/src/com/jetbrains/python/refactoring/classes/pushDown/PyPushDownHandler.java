package com.jetbrains.python.refactoring.classes.pushDown;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.refactoring.classes.PyClassMembersRefactoringSupport;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringHandler;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;

import java.util.Collection;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownHandler extends PyClassRefactoringHandler implements ElementsHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("pull.members.up.title");

  @Override
  protected void doRefactor(Project project, PsiElement element1, PsiElement element2, Editor editor, PsiFile file, DataContext dataContext) {
    CommonRefactoringUtil.checkReadOnlyStatus(project, file);

    final PyClass clazz = PyUtil.getContainingClassOrSelf(element1);
    if (!inClass(clazz, project, editor, "refactoring.pull.up.error.cannot.perform.refactoring.not.inside.class")) return;

    final PyMemberInfoStorage infoStorage = PyClassMembersRefactoringSupport.getSelectedMemberInfos(clazz, element1, element2);
    final Collection<PyClass> classes = infoStorage.getClasses();
    if (classes.size() == 0) {
      assert clazz != null;
      CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.pull.up.error.cannot.perform.refactoring.no.base.classes", clazz.getName()),
                                          RefactoringBundle.message("pull.members.up.title"),
                                          "members.pull.up");
      return;
    }

    if (ApplicationManagerEx.getApplicationEx().isUnitTestMode()) return;

    final PyPushDownDialog dialog = new PyPushDownDialog(project, clazz, infoStorage);
    dialog.show();
  }

  @Override
  protected String getTitle() {
    return REFACTORING_NAME;
  }

  @Override
  protected String getHelpId() {
    return "members.pull.up";
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    return elements.length == 1 && elements[0] instanceof PyClass;
  }

}
