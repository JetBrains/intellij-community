package com.jetbrains.python.refactoring.classes.membersManager;

import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Plugin that moves class methods
 *
 * @author Ilya.Kazakevich
 */
class MethodsManager extends MembersManager {
  @NotNull
  @Override
  protected List<PyElement> getMembersCouldBeMoved(@NotNull final PyClass pyClass) {
    return Arrays.<PyElement>asList(pyClass.getMethods());
  }

  @Override
  protected void moveMembers(@NotNull PyClass from, @NotNull PyClass to, @NotNull Collection<PyElement> members) {
    //TODO: Use generics to prevent casting in each subclass
    Collection<PyFunction> members1 = (Collection)members;
    PyClassRefactoringUtil.moveMethods(members1, to);
  }

  @NotNull
  @Override
  public PyMemberInfo apply(@NotNull final PyElement input) {
    //TODO: Use generics to prevent casting in each subclass
    final PyFunction pyFunction = (PyFunction)input;
    //TODO: Support static and classmethod functions
    return new PyMemberInfo(input, false, buildDisplayMethodName(pyFunction), isOverrides(pyFunction), this);
  }

  @Nullable
  private static Boolean isOverrides(final PyFunction pyFunction) {
    final PyClass clazz = PyUtil.getContainingClassOrSelf(pyFunction);
    assert clazz != null : "Refactoring called on function, not method: " + pyFunction;
    for (final PyClass parentClass : clazz.getSuperClasses()) {
      final PyFunction parentMethod = parentClass.findMethodByName(pyFunction.getName(), true);
      if (parentMethod != null) {
        return true;
      }
    }
    return null;
  }

  @NotNull
  private static String buildDisplayMethodName(@NotNull final PyFunction pyFunction) {
    final StringBuilder builder = new StringBuilder(pyFunction.getName());
    builder.append('(');
    final PyParameter[] arguments = pyFunction.getParameterList().getParameters();
    for (final PyParameter parameter : arguments) {
      builder.append(parameter.getName());
      if (arguments.length > 1 && parameter != arguments[arguments.length - 1]) {
        builder.append(", ");
      }
    }
    builder.append(')');
    return builder.toString();
  }
}
