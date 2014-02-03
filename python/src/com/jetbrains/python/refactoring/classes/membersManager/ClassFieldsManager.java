package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Moves class attributes up
 * @author Ilya.Kazakevich
 */
class ClassFieldsManager extends MembersManager<PyTargetExpression> {

  ClassFieldsManager() {
    super(PyTargetExpression.class);
  }

  @NotNull
  @Override
  protected List<PyElement> getMembersCouldBeMoved(@NotNull final PyClass pyClass) {
    return new ArrayList<PyElement>(Collections2.filter(pyClass.getClassAttributes(), new SimpleAssignmentsOnly()));
  }

  @Override
  protected void moveMembers(@NotNull final PyClass from, @NotNull final PyClass to, @NotNull final Collection<PyTargetExpression> members) {
    PyClassRefactoringUtil.moveFieldDeclarationToStatement(members, to.getStatementList());
  }

  @NotNull
  @Override
  public PyMemberInfo apply(@NotNull final PyElement input) {
    return new PyMemberInfo(input, true, input.getText(), false, this); //TODO: Check overrides
  }

  private static class SimpleAssignmentsOnly implements Predicate<PyTargetExpression> {
    //Support only simplest cases like CLASS_VAR = 42.
    //Tuples (CLASS_VAR_1, CLASS_VAR_2) = "spam", "eggs" are not supported by now
    @Override
    public boolean apply(@Nullable final PyTargetExpression input) {
      if (input == null) {
        return false; //Filter out empties (which probably would never be here)
      }
      final PsiElement parent = input.getParent();
      return (parent != null) && PyAssignmentStatement.class.isAssignableFrom(parent.getClass());
    }
  }
}
