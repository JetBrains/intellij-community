/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.collect.Collections2;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.NotNullPredicate;
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
  public boolean hasConflict(@NotNull final PyTargetExpression member, @NotNull final PyClass aClass) {
    return NamePredicate.hasElementWithSameName(member, aClass.getInstanceAttributes());
  }

  @Override
  protected Collection<PyElement> moveAssignments(@NotNull final PyClass from,
                                                  @NotNull final Collection<PyAssignmentStatement> statements,
                                                  @NotNull final PyClass... to) {
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
  @NotNull
  private static List<PyAssignmentStatement> copyInstanceFields(@NotNull final Collection<PyAssignmentStatement> members,
                                                                @NotNull final PyClass to) {
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
  @NotNull
  private static PyFunction createInitMethod(@NotNull final PyClass to) {
    final PyFunctionBuilder functionBuilder = new PyFunctionBuilder(PyNames.INIT, to);
    functionBuilder.parameter(PyNames.CANONICAL_SELF); //TODO: Take param from codestyle?
    final PyFunction function = functionBuilder.buildFunction(to.getProject(), LanguageLevel.forElement(to));
    return PyClassRefactoringUtil.addMethods(to, true, function).get(0);
  }

  @Override
  protected boolean classHasField(@NotNull final PyClass pyClass, @NotNull final String fieldName) {
    return pyClass.findInstanceAttribute(fieldName, true) != null;
  }

  @NotNull
  @Override
  protected Collection<PyTargetExpression> getFieldsByClass(@NotNull final PyClass pyClass) {
    return Collections2.filter(pyClass.getInstanceAttributes(), FIELDS_ONLY);
  }

  private static class InitsOnly extends NotNullPredicate<PyAssignmentStatement> {
    @NotNull
    private final PyFunction myInitMethod;

    private InitsOnly(@NotNull final PyFunction initMethod) {
      myInitMethod = initMethod;
    }

    @Override
    protected boolean applyNotNull(@NotNull final PyAssignmentStatement input) {
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
    protected boolean applyNotNull(@NotNull final PyTargetExpression input) {
      return input.getReference().resolve() instanceof PyTargetExpression;
    }
  }
}
