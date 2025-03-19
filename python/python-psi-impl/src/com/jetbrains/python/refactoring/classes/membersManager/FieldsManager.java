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

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.NotNullPredicate;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Parent of all field-based plugins (like class fields, instance fields and so on)
 *
 * @author Ilya.Kazakevich
 */
abstract class FieldsManager extends MembersManager<PyTargetExpression> {
  private static final SimpleAssignmentsOnly SIMPLE_ASSIGNMENTS_ONLY = new SimpleAssignmentsOnly();
  private static final AssignmentTransform ASSIGNMENT_TRANSFORM = new AssignmentTransform();
  private final boolean myStatic;

  /**
   * @param isStatic is field static or not?
   */
  protected FieldsManager(final boolean isStatic) {
    super(PyTargetExpression.class);
    myStatic = isStatic;
  }


  @Override
  protected @NotNull Collection<PyElement> getDependencies(final @NotNull MultiMap<PyClass, PyElement> usedElements) {
    return Collections.emptyList();
  }

  @Override
  protected @NotNull MultiMap<PyClass, PyElement> getDependencies(final @NotNull PyElement member) {
    final PyRecursiveElementVisitorWithResult visitor = new MyPyRecursiveElementVisitor();
    member.accept(visitor);
    return visitor.myResult;
  }

  @Override
  protected Collection<? extends PyElement> getElementsToStoreReferences(final @NotNull Collection<PyTargetExpression> elements) {
    // We need to save references from assignments
    return Collections2.transform(elements, ASSIGNMENT_TRANSFORM);
  }

  @Override
  protected @NotNull List<PyElement> getMembersCouldBeMoved(final @NotNull PyClass pyClass) {
    return Lists.newArrayList(Collections2.filter(getFieldsByClass(pyClass), SIMPLE_ASSIGNMENTS_ONLY));
  }

  @Override
  protected Collection<PyElement> moveMembers(final @NotNull PyClass from,
                                              final @NotNull Collection<PyMemberInfo<PyTargetExpression>> members,
                                              final PyClass @NotNull ... to) {
    return moveAssignments(from, Collections2
      .filter(Collections2.transform(fetchElements(members), ASSIGNMENT_TRANSFORM), NotNullPredicate.INSTANCE),
                           to);
  }

  protected abstract Collection<PyElement> moveAssignments(@NotNull PyClass from,
                                                           @NotNull Collection<PyAssignmentStatement> statements,
                                                           PyClass @NotNull ... to);

  /**
   * Checks if class has fields. Only child may know how to obtain field
   *
   * @param pyClass   class to check
   * @param fieldName field name
   * @return true if has one
   */
  protected abstract boolean classHasField(@NotNull PyClass pyClass, @NotNull String fieldName);

  /**
   * Returns all fields by class. Only child may know how to obtain fields
   *
   * @param pyClass class to check
   * @return list of fields in target expression (declaration) form
   */
  protected abstract @NotNull Collection<PyTargetExpression> getFieldsByClass(@NotNull PyClass pyClass);


  @Override
  public @NotNull PyMemberInfo<PyTargetExpression> apply(final @NotNull PyTargetExpression input) {
    var parent = ObjectUtils.tryCast(input.getParent(), PyAssignmentStatement.class);
    return new PyMemberInfo<>(input, parent != null ? parent : input, myStatic, input.getText(), isOverrides(input), this, false);
  }

  private @Nullable Boolean isOverrides(final @NotNull PyTargetExpression input) {
    final PyClass aClass = input.getContainingClass();
    final String name = input.getName();
    if (name == null) {
      return null; //Field with out of name can't override something
    }

    assert aClass != null : "Target expression declared outside of class:" + input;

    return classHasField(aClass, name) ? true : null;
  }


  private static class SimpleAssignmentsOnly extends NotNullPredicate<PyTargetExpression> {
    //Support only simplest cases like CLASS_VAR = 42.
    //Tuples (CLASS_VAR_1, CLASS_VAR_2) = "spam", "eggs" are not supported by now
    @Override
    public boolean applyNotNull(final @NotNull PyTargetExpression input) {
      final PsiElement parent = input.getParent();
      return (parent != null) && PyAssignmentStatement.class.isAssignableFrom(parent.getClass());
    }
  }


  //Transforms expressions to its assignment step
  private static class AssignmentTransform implements Function<PyTargetExpression, PyAssignmentStatement> {
    @Override
    public @Nullable PyAssignmentStatement apply(final @NotNull PyTargetExpression input) {
      return PsiTreeUtil.getParentOfType(input, PyAssignmentStatement.class);
    }
  }

  /**
   * Fetches field declarations
   */
  private static class MyPyRecursiveElementVisitor extends PyRecursiveElementVisitorWithResult {

    @Override
    public void visitPyReferenceExpression(final @NotNull PyReferenceExpression node) {
      final PsiElement declaration = node.getReference().resolve();
      if (declaration instanceof PyElement) {
        final PyClass parent = PsiTreeUtil.getParentOfType(declaration, PyClass.class);
        if (parent != null) {
          myResult.putValue(parent, (PyElement)declaration);
        }
      }
    }
  }
}
