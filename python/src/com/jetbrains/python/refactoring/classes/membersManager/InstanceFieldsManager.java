package com.jetbrains.python.refactoring.classes.membersManager;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Ilya.Kazakevich
 */
class InstanceFieldsManager extends FieldsManager {
  InstanceFieldsManager() {
    super(false);
  }


  @Override
  protected Collection<PyElement> moveAssignments(@NotNull final PyClass from,
                                 @NotNull final Collection<PyAssignmentStatement> statements,
                                 @NotNull final PyClass... to) {
    //TODO: Copy/paste with ClassFieldsManager. Move to parent?

    List<PyElement> result = new ArrayList<PyElement>();
    for (final PyClass destClass : to) {
      result.addAll(copyInstanceFields(statements, destClass));
    }

    deleteElements(statements);

    final PyFunction fromInitMethod = from.findMethodByName(PyNames.INIT, false);
    if (fromInitMethod != null) {
      //We can't leave class constructor with empty body
      PyClassRefactoringUtil.insertPassIfNeeded(fromInitMethod);
    }
    return result;
  }

   /**
   * Copies class' fields in form of assignments (instance fields) to another class.
    * Creates init method if there is no any
   * @param members assignments to copy
   * @param to destination
   * @return newly created fields
   */
  @NotNull
  private static List<PyAssignmentStatement> copyInstanceFields(@NotNull final Collection<PyAssignmentStatement> members,
                                                                @NotNull final PyClass to) {
    //We need __init__ method, and if there is no any -- we need to create it
    PyFunction toInitMethod = to.findMethodByName(PyNames.INIT, false);
    if (toInitMethod == null) {
      toInitMethod = PyClassRefactoringUtil.createMethod(PyNames.INIT, to, null);
    }
    final PyStatementList statementList = toInitMethod.getStatementList();
    return PyClassRefactoringUtil.copyFieldDeclarationToStatement(members, statementList);
  }

  @Override
  protected boolean classHasField(@NotNull final PyClass pyClass, @NotNull final String fieldName) {
    return pyClass.findInstanceAttribute(fieldName, true) != null;
  }

  @NotNull
  @Override
  protected List<PyTargetExpression> getFieldsByClass(@NotNull final PyClass pyClass) {
    return pyClass.getInstanceAttributes();
  }
}
