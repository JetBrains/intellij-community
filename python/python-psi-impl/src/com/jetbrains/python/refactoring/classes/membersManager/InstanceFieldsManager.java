// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.collect.Collections2;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.NotNullPredicate;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Ilya.Kazakevich
 */
class InstanceFieldsManager extends FieldsManager {
  private static final FieldsOnly FIELDS_ONLY = new FieldsOnly();

  // PY-12170

  InstanceFieldsManager() {
    super(false);
  }

  @Override
  public boolean hasConflict(final @NotNull PyTargetExpression member, final @NotNull PyClass aClass) {
    return NamePredicate.hasElementWithSameName(member, aClass.getInstanceAttributes());
  }

  @Override
  protected Collection<PyElement> moveAssignments(final @NotNull PyClass from,
                                                  final @NotNull Collection<PyAssignmentStatement> statements,
                                                  final PyClass @NotNull ... to) {
    //TODO: Copy/paste with ClassFieldsManager. Move to parent?

    final List<PyElement> result = new ArrayList<>();
    for (final PyClass destClass : to) {
      result.addAll(copyInstanceFields(statements, destClass));
    }
    // Delete only declarations made in __init__ to prevent PY-12170
    final PyFunction fromInitMethod = PyUtil.getInitMethod(from);
    if (fromInitMethod != null) { // If class has no init method that means all its fields declared in other methods, so nothing to remove
      deleteElements(Collections2.filter(statements, new InitsOnly(fromInitMethod)));
      //We can't leave class constructor with empty body
    }
    return result;
  }

  /**
   * Copies class' fields in form of assignments (instance fields) to another class.
   * Creates init method if there is no any
   *
   * @param members assignments to copy
   * @param to      destination
   * @return newly created fields
   */
  private static @NotNull List<PyAssignmentStatement> copyInstanceFields(final @NotNull Collection<PyAssignmentStatement> members,
                                                                final @NotNull PyClass to) {
    //We need __init__ method, and if there is no any -- we need to create it
    PyFunction toInitMethod = PyUtil.getInitMethod(to);
    if (toInitMethod == null) {
      toInitMethod = createInitMethod(to);
    }
    final PyStatementList statementList = toInitMethod.getStatementList();
    return PyClassRefactoringUtil.copyFieldDeclarationToStatement(members, statementList, null);
  }

  /**
   * Creates init method and adds it to certain class.
   *
   * @param to Class where method should be added
   * @return newly created method
   */
  //TODO: Move to utils?
  private static @NotNull PyFunction createInitMethod(final @NotNull PyClass to) {
    final PyFunctionBuilder functionBuilder = new PyFunctionBuilder(PyNames.INIT, to);
    functionBuilder.parameter(PyNames.CANONICAL_SELF); //TODO: Take param from codestyle?
    final PyFunction function = functionBuilder.buildFunction();
    return PyClassRefactoringUtil.addMethods(to, true, function).get(0);
  }

  @Override
  protected boolean classHasField(final @NotNull PyClass pyClass, final @NotNull String fieldName) {
    return pyClass.findInstanceAttribute(fieldName, true) != null;
  }

  @Override
  protected @NotNull Collection<PyTargetExpression> getFieldsByClass(final @NotNull PyClass pyClass) {
    return Collections2.filter(pyClass.getInstanceAttributes(), FIELDS_ONLY);
  }

  private static final class InitsOnly extends NotNullPredicate<PyAssignmentStatement> {
    private final @NotNull PyFunction myInitMethod;

    private InitsOnly(final @NotNull PyFunction initMethod) {
      myInitMethod = initMethod;
    }

    @Override
    protected boolean applyNotNull(final @NotNull PyAssignmentStatement input) {
      final PyExpression expression = input.getLeftHandSideExpression();
      if (expression == null) {
        return false;
      }

      final PyFunction functionWhereDeclared = PsiTreeUtil.getParentOfType(PyUtil.resolveToTheTop(expression), PyFunction.class);
      return myInitMethod.equals(functionWhereDeclared);
    }
  }

  private static class FieldsOnly extends NotNullPredicate<PyTargetExpression> {
    @Override
    protected boolean applyNotNull(final @NotNull PyTargetExpression input) {
      return input.getReference().resolve() instanceof PyTargetExpression;
    }
  }
}
