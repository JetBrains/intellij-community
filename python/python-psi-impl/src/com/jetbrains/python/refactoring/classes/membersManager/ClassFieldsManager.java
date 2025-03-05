// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.collect.FluentIterable;
import com.jetbrains.python.NotNullPredicate;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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
  public boolean hasConflict(final @NotNull PyTargetExpression member, final @NotNull PyClass aClass) {
    return NamePredicate.hasElementWithSameName(member, aClass.getClassAttributes());
  }

  @Override
  protected Collection<PyElement> moveAssignments(final @NotNull PyClass from,
                                                  final @NotNull Collection<PyAssignmentStatement> statements,
                                                  final PyClass @NotNull ... to) {
    return moveAssignmentsImpl(from, statements, to);
  }

  /**
   * Moves assignments from one class to anothers
   * @param from source
   * @param statements assignments
   * @param to destination
   * @return newly created assignments
   */
  static Collection<PyElement> moveAssignmentsImpl(final @NotNull PyClass from,
                                                   final @NotNull Collection<? extends PyAssignmentStatement> statements,
                                                   final PyClass @NotNull ... to) {
    //TODO: Copy/paste with InstanceFieldsManager. Move to parent?
    final Collection<PyElement> result = new ArrayList<>();
    for (final PyClass destClass : to) {
      result.addAll(PyClassRefactoringUtil.copyFieldDeclarationToStatement(statements, destClass.getStatementList(), destClass));
    }
    deleteElements(statements);
    return result;
  }

  @Override
  protected boolean classHasField(final @NotNull PyClass pyClass, final @NotNull String fieldName) {
    return pyClass.findClassAttribute(fieldName, true, null) != null;
  }

  @Override
  protected @NotNull List<PyTargetExpression> getFieldsByClass(final @NotNull PyClass pyClass) {
    return FluentIterable.from(pyClass.getClassAttributes()).filter(new NoMetaAndProperties(pyClass)).toList();
  }

  /**
   * Exclude "__metaclass__" field and properties (there should be separate managers for them)
   * TODO: Check type and filter out any builtin element instead?
   */
  private static final class NoMetaAndProperties extends NotNullPredicate<PyTargetExpression> {
    private final @NotNull PyClass myClass;

    private NoMetaAndProperties(final @NotNull PyClass aClass) {
      myClass = aClass;
    }

    @Override
    public boolean applyNotNull(final @NotNull PyTargetExpression input) {
      final String name = input.getName();
      if (name == null) {
        return false;
      }
      if (name.equals(PyNames.DUNDER_METACLASS)) {
        return false;
      }

      final PyExpression assignedValue = input.findAssignedValue();
      if (assignedValue instanceof PyCallExpression) {
        final PyExpression callee = ((PyCallExpression)assignedValue).getCallee();
        if ((callee != null) && PyNames.PROPERTY.equals(callee.getName()) && (myClass.findProperty(name, false, null) != null)) {
          return false;
        }
      }
      return true;
    }
  }
}
