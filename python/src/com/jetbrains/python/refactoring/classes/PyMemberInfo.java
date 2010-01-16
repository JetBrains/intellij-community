package com.jetbrains.python.refactoring.classes;

import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.jetbrains.python.psi.*;

/**
 * @author Dennis.Ushakov
 */
public class PyMemberInfo extends MemberInfoBase<PyElement> {
  public PyMemberInfo(PyElement member) {
    super(member);
    final PyClass clazz = PyUtil.getContainingClassOrSelf(member);
    assert clazz != null;

    if (member instanceof PyFunction) {
      PyFunction function = (PyFunction)member;
      displayName = buildDisplayMethodName(function);
      for (PyClass aClass : clazz.getSuperClasses()) {
        final PyFunction parentMethod = aClass.findMethodByName(function.getName(), true);
        if (parentMethod != null) {
          overrides = true;
        }
      }
    }
  }

  private static String buildDisplayMethodName(PyFunction method) {
    final StringBuilder builder = new StringBuilder(method.getName());
    builder.append("(");
    final PyParameter[] arguments = method.getParameterList().getParameters();
    for (PyParameter parameter : arguments) {
      builder.append(parameter.getName());
      if (arguments.length > 1 && parameter != arguments[arguments.length - 1]) {
        builder.append(", ");
      }
    }
    builder.append(")");
    return builder.toString();
  }
}
