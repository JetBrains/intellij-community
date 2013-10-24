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
package com.jetbrains.numpy.codeInsight;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.numpy.documentation.NumPyDocString;
import com.jetbrains.numpy.documentation.NumPyDocStringParameter;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Provides type information extracted from NumPy docstring format.
 *
 * @author avereshchagin
 * @author vlan
 */
public class NumpyDocStringTypeProvider extends PyTypeProviderBase {
  private static final Map<String, String> NUMPY_ALIAS_TO_REAL_TYPE = new HashMap<String, String>();

  static {
    NUMPY_ALIAS_TO_REAL_TYPE.put("ndarray", "numpy.core.multiarray.ndarray");
    // 184 occurrences
    NUMPY_ALIAS_TO_REAL_TYPE.put("array_like", "collections.Iterable or int or long or float or complex");
    // Parameters marked as 'data-type' actually get any Python type identifier such as 'bool' or
    // an instance of 'numpy.core.multiarray.dtype', however the type checker isn't able to check it.
    // 30 occurrences
    NUMPY_ALIAS_TO_REAL_TYPE.put("data-type", "object");
    // 16 occurrences
    NUMPY_ALIAS_TO_REAL_TYPE.put("scalar", "int or long or float or complex");
    // 10 occurrences
    NUMPY_ALIAS_TO_REAL_TYPE.put("array", "collections.Iterable");
    // 9 occurrences
    NUMPY_ALIAS_TO_REAL_TYPE.put("any", "object");
    // 5 occurrences
    NUMPY_ALIAS_TO_REAL_TYPE.put("Standard Python scalar object", "int or long or float or complex");
    // 4 occurrences
    NUMPY_ALIAS_TO_REAL_TYPE.put("Python type", "object");
    // 3 occurrences
    NUMPY_ALIAS_TO_REAL_TYPE.put("callable", "collections.Callable");
    // 3 occurrences
    NUMPY_ALIAS_TO_REAL_TYPE.put("number", "int or long or float or complex");
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull PyFunction function, @Nullable PyQualifiedExpression callSite, @NotNull TypeEvalContext context) {
    if (isInsideNumPy(function)) {
      final NumPyDocString docString = NumPyDocString.forFunction(function, callSite);
      if (docString != null) {
        final List<NumPyDocStringParameter> returns = docString.getReturns();
        final PyPsiFacade facade = getPsiFacade(function);
        switch (returns.size()) {
          case 0:
            // Function returns nothing
            return facade.parseTypeAnnotation("None", function);
          case 1:
            // Function returns single value
            final String typeName = returns.get(0).getType();
            if (typeName != null) {
              return parseNumpyDocType(function, typeName);
            }
            return null;
          default:
            // Function returns a tuple
            final List<PyType> members = new ArrayList<PyType>();
            for (NumPyDocStringParameter ret : returns) {
              final String memberTypeName = ret.getType();
              members.add(memberTypeName != null ? parseNumpyDocType(function, memberTypeName) : null);
            }
            return facade.createTupleType(members, function);
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getParameterType(@NotNull PyNamedParameter parameter, @NotNull PyFunction function, @NotNull TypeEvalContext context) {
    if (isInsideNumPy(function)) {
      final String name = parameter.getName();
      if (name != null) {
        return getParameterType(function, name);
      }
    }
    return null;
  }

  private static boolean isInsideNumPy(@NotNull PsiElement element) {
    final PsiFile file = element.getContainingFile();
    if (file != null) {
      final PyPsiFacade facade = getPsiFacade(element);
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        final String name = facade.findShortestImportableName(virtualFile, element);
        return name != null && name.startsWith("numpy.");
      }
    }
    return false;
  }

  private static PyPsiFacade getPsiFacade(@NotNull PsiElement anchor) {
    return PyPsiFacade.getInstance(anchor.getProject());
  }

  @Nullable
  private static PyType parseSingleNumpyDocType(@NotNull PsiElement anchor, @NotNull String typeString) {
    final PyPsiFacade facade = getPsiFacade(anchor);
    final String realTypeName = NUMPY_ALIAS_TO_REAL_TYPE.get(typeString);
    if (realTypeName != null) {
      final PyType type = facade.parseTypeAnnotation(realTypeName, anchor);
      if (type != null) {
        return type;
      }
    }
    return facade.parseTypeAnnotation(typeString, anchor);
  }

  @Nullable
  private static PyType parseNumpyDocType(@NotNull PsiElement anchor, @NotNull String typeString) {
    typeString = NumPyDocString.cleanupOptional(typeString);
    final Set<PyType> types = new LinkedHashSet<PyType>();
    for (String typeName : NumPyDocString.getNumpyUnionType(typeString)) {
      PyType parsedType = parseSingleNumpyDocType(anchor, typeName);
      if (parsedType != null) {
        types.add(parsedType);
      }
    }
    return getPsiFacade(anchor).createUnionType(types);
  }

  @Nullable
  private PyType getParameterType(@NotNull PyFunction function, @NotNull String parameterName) {
    final NumPyDocString docString = NumPyDocString.forFunction(function, function);
    if (docString != null) {
      NumPyDocStringParameter parameter = docString.getNamedParameter(parameterName);

      // If parameter name starts with "p_", and we failed to obtain it from the docstring,
      // try to obtain parameter named without such prefix.
      if (parameter == null && parameterName.startsWith("p_")) {
        parameter = docString.getNamedParameter(parameterName.substring(2));
      }
      if (parameter != null) {
        return parseNumpyDocType(function, parameter.getType());
      }
    }
    return null;
  }
}
