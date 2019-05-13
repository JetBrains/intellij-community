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
package com.jetbrains.pyqt;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : ktisha
 */
public class PyQtTypeProvider extends PyTypeProviderBase {
  private static final String ourQtBoundSignal = "QtCore.pyqtBoundSignal";
  private static final String ourQt4Signal = "pyqtSignal";

  @Override
  public Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    if (PyNames.INIT.equals(callable.getName()) && callable instanceof PyFunction) {
      final PyFunction function = (PyFunction)callable;
      final PyClass containingClass = function.getContainingClass();
      if (containingClass != null && ourQt4Signal.equals(containingClass.getName())) {
        final String classQName = containingClass.getQualifiedName();
        if (classQName != null) {
          final QualifiedName name = QualifiedName.fromDottedString(classQName);
          final String qtVersion = name.getComponents().get(0);
          final PyClass aClass = PyClassNameIndex.findClass(qtVersion + "." + ourQtBoundSignal, function.getProject());
          if (aClass != null) {
            final PyType type = new PyClassTypeImpl(aClass, false);
            return Ref.create(type);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getCallableType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    if (callable instanceof PyFunction) {
      final String qualifiedName = callable.getQualifiedName();
      if (qualifiedName != null && qualifiedName.startsWith("PyQt")){
        final QualifiedName name = QualifiedName.fromDottedString(qualifiedName);
        final String qtVersion = name.getComponents().get(0);
        final String docstring = ((PyFunction)callable).getDocStringValue();
        if (docstring != null && docstring.contains("[signal]")) {
          final PyClass aClass = PyClassNameIndex.findClass(qtVersion + "." + ourQtBoundSignal, callable.getProject());
          if (aClass != null)
            return new PyClassTypeImpl(aClass, false);
        }
      }
    }
    return null;
  }
}
