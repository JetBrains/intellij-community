package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Parent of all field-based plugins (like class fields, instance fields and so on)
 *
 * @author Ilya.Kazakevich
 */
abstract class FieldsManager extends MembersManager<PyTargetExpression> {
  private static final SimpleAssignmentsOnly SIMPLE_ASSIGNMENTS_ONLY = new SimpleAssignmentsOnly();
  private final boolean myStatic;

  /**
   * @param isStatic is field static or not?
   */
  protected FieldsManager(final boolean isStatic) {
    super(PyTargetExpression.class);
    myStatic = isStatic;
  }

  @NotNull
  @Override
  protected List<PyElement> getMembersCouldBeMoved(@NotNull final PyClass pyClass) {
    return Lists.<PyElement>newArrayList(Collections2.filter(getFieldsByClass(pyClass), SIMPLE_ASSIGNMENTS_ONLY));
  }

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
  @NotNull
  protected abstract List<PyTargetExpression> getFieldsByClass(@NotNull PyClass pyClass);


  @NotNull
  @Override
  public PyMemberInfo apply(@NotNull final PyTargetExpression input) {
    return new PyMemberInfo(input, myStatic, input.getText(), isOverrides(input), this);
  }

  @Nullable
  private Boolean isOverrides(@NotNull final PyTargetExpression input) {
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
    public boolean applyNotNull(@NotNull final PyTargetExpression input) {
      final PsiElement parent = input.getParent();
      return (parent != null) && PyAssignmentStatement.class.isAssignableFrom(parent.getClass());
    }
  }
}
