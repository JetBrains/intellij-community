package com.jetbrains.python.refactoring.classes.membersManager;

import com.jetbrains.python.psi.PyAssignmentStatement;
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
  protected void moveAssignments(@NotNull final PyClass from,
                                 @NotNull final Collection<PyAssignmentStatement> statements,
                                 @NotNull final PyClass... to) {
    //TODO: Copy/paste with InstanceFieldsManager. Move to parent?
    for (final PyClass destClass : to) {
      PyClassRefactoringUtil.copyFieldDeclarationToStatement(statements, destClass.getStatementList());
    }
    deleteElements(statements);
    PyClassRefactoringUtil.insertPassIfNeeded(from);
  }

  @Override
  protected boolean classHasField(@NotNull final PyClass pyClass, @NotNull final String fieldName) {
    return pyClass.findClassAttribute(fieldName, true) != null;
  }

  @NotNull
  @Override
  protected List<PyTargetExpression> getFieldsByClass(@NotNull final PyClass pyClass) {
    return pyClass.getClassAttributes();
  }
}
