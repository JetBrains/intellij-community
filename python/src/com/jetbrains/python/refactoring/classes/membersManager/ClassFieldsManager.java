package com.jetbrains.python.refactoring.classes.membersManager;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Moves class attributes up
 *
 * @author Ilya.Kazakevich
 */
class ClassFieldsManager extends FieldsManager {

  ClassFieldsManager() {
    super(true);
  }


  @Override
  protected void moveMembers(@NotNull final PyClass from,
                             @NotNull final PyClass to,
                             @NotNull final Collection<PyTargetExpression> members) {
    PyClassRefactoringUtil.moveFieldDeclarationToStatement(members, to.getStatementList());
  }

  @Override
  protected boolean classHasField(@NotNull final PyClass pyClass, @NotNull final String fieldName) {
    return pyClass.findClassAttribute(fieldName, true) != null;
  }

  @NotNull
  @Override
  protected List<PyTargetExpression> getFieldsByClass(@NotNull PyClass pyClass) {
    return pyClass.getClassAttributes();
  }
}
