/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.classes;

import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.classes.ui.PyClassCellRenderer;

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
    } else if (member instanceof PyClass) {
      displayName = RefactoringBundle.message("member.info.extends.0", PyClassCellRenderer.getClassText((PyClass)member));
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

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof PyMemberInfo) {
      return getMember().equals(((PyMemberInfo)obj).getMember());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return getMember().hashCode();
  }
}
