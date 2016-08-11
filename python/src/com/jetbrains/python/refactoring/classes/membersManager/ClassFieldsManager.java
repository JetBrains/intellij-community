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

import com.google.common.collect.FluentIterable;
import com.jetbrains.NotNullPredicate;
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
  public boolean hasConflict(@NotNull final PyTargetExpression member, @NotNull final PyClass aClass) {
    return NamePredicate.hasElementWithSameName(member, aClass.getClassAttributes());
  }

  @Override
  protected Collection<PyElement> moveAssignments(@NotNull final PyClass from,
                                                  @NotNull final Collection<PyAssignmentStatement> statements,
                                                  @NotNull final PyClass... to) {
    return moveAssignmentsImpl(from, statements, to);
  }

  /**
   * Moves assignments from one class to anothers
   * @param from source
   * @param statements assignments
   * @param to destination
   * @return newly created assignments
   */
  static Collection<PyElement> moveAssignmentsImpl(@NotNull final PyClass from,
                                                   @NotNull final Collection<PyAssignmentStatement> statements,
                                                   @NotNull final PyClass... to) {
    //TODO: Copy/paste with InstanceFieldsManager. Move to parent?
    final Collection<PyElement> result = new ArrayList<>();
    for (final PyClass destClass : to) {
      result.addAll(PyClassRefactoringUtil.copyFieldDeclarationToStatement(statements, destClass.getStatementList(), destClass));
    }
    deleteElements(statements);
    return result;
  }

  @Override
  protected boolean classHasField(@NotNull final PyClass pyClass, @NotNull final String fieldName) {
    return pyClass.findClassAttribute(fieldName, true, null) != null;
  }

  @NotNull
  @Override
  protected List<PyTargetExpression> getFieldsByClass(@NotNull final PyClass pyClass) {
    return FluentIterable.from(pyClass.getClassAttributes()).filter(new NoMetaAndProperties(pyClass)).toList();
  }

  /**
   * Exclude "__metaclass__" field and properties (there should be separate managers for them)
   * TODO: Check type and filter out any builtin element instead?
   */
  private static class NoMetaAndProperties extends NotNullPredicate<PyTargetExpression> {
    @NotNull
    private final PyClass myClass;

    private NoMetaAndProperties(@NotNull final PyClass aClass) {
      myClass = aClass;
    }

    @Override
    public boolean applyNotNull(@NotNull final PyTargetExpression input) {
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
