package com.jetbrains.python.refactoring.classes.membersManager;

import com.google.common.collect.Lists;
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
class MethodsManager extends MembersManager<PyFunction> {

  MethodsManager() {
    super(PyFunction.class);
  }

  @NotNull
  @Override
  protected List<PyElement> getMembersCouldBeMoved(@NotNull final PyClass pyClass) {
    return Lists.<PyElement>newArrayList(filterNameless(Arrays.asList(pyClass.getMethods())));
  }

  @Override
  protected void moveMembers(@NotNull final PyClass from, @NotNull final PyClass to, @NotNull final Collection<PyFunction> members) {
    PyClassRefactoringUtil.moveMethods(members, to);
  }

  @NotNull
  @Override
  public PyMemberInfo apply(@NotNull final PyFunction pyFunction) {
    final PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(pyFunction);
    assert flags != null : "No flags return while element is function " + pyFunction;
    final boolean isStatic = flags.isStaticMethod() || flags.isClassMethod();
    return new PyMemberInfo(pyFunction, isStatic, buildDisplayMethodName(pyFunction), isOverrides(pyFunction), this);
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
