/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.testing.pytest;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.testing.PythonUnitTestUtil;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 *
 * @deprecated use {@link PythonUnitTestUtil} will be removed in 2018
 */
@Deprecated
public class PyTestUtil {


  @Deprecated
  public static boolean isPyTestFunction(PyFunction pyFunction) {
    String name = pyFunction.getName();
    if (name != null && name.startsWith("test")) {
      return true;
    }
    return false;
  }

  @Deprecated
  public static boolean isPyTestClass(final PyClass pyClass, @Nullable final TypeEvalContext context) {
    final TypeEvalContext contextToUse = (context != null ? context : TypeEvalContext.codeInsightFallback(pyClass.getProject()));
    for (PyClassLikeType type : pyClass.getAncestorTypes(contextToUse)) {
      if (type != null && PythonUnitTestUtil.PYTHON_TEST_QUALIFIED_CLASSES.contains(type.getClassQName())) {
        return true;
      }
    }
    final String className = pyClass.getName();
    if (className == null) return false;
    final String name = className.toLowerCase();
    if (name.startsWith("test")) {
      for (PyFunction cls : pyClass.getMethods()) {
        if (isPyTestFunction(cls)) {
          return true;
        }
      }
    }
    return false;
  }
}
