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
package com.jetbrains.numpy.codeInsight;

import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.numpy.documentation.NumPyDocString;
import com.jetbrains.numpy.documentation.NumPyDocStringParameter;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
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
  public static String NDARRAY = "numpy.core.multiarray.ndarray";

  private static String NDARRAY_OR_ITERABLE = NDARRAY + " or collections.Iterable";

  static {
    NUMPY_ALIAS_TO_REAL_TYPE.put("ndarray", NDARRAY);
    NUMPY_ALIAS_TO_REAL_TYPE.put("numpy.ndarray", NDARRAY);
    // 184 occurrences

    NUMPY_ALIAS_TO_REAL_TYPE.put("array_like", NDARRAY_OR_ITERABLE);

    NUMPY_ALIAS_TO_REAL_TYPE.put("array-like", NDARRAY_OR_ITERABLE);
    // Parameters marked as 'data-type' actually get any Python type identifier such as 'bool' or
    // an instance of 'numpy.core.multiarray.dtype', however the type checker isn't able to check it.
    // 30 occurrences
    NUMPY_ALIAS_TO_REAL_TYPE.put("data-type", "object");
    NUMPY_ALIAS_TO_REAL_TYPE.put("dtype", "object");
    // 16 occurrences
    NUMPY_ALIAS_TO_REAL_TYPE.put("scalar", "int or long or float or complex");
    // 10 occurrences
    NUMPY_ALIAS_TO_REAL_TYPE.put("array", NDARRAY_OR_ITERABLE);
    NUMPY_ALIAS_TO_REAL_TYPE.put("numpy.array", NDARRAY_OR_ITERABLE);
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

    //treat all collections as iterable
    NUMPY_ALIAS_TO_REAL_TYPE.put("sequence", "collections.Iterable");
    NUMPY_ALIAS_TO_REAL_TYPE.put("set", "collections.Iterable");
    NUMPY_ALIAS_TO_REAL_TYPE.put("list", "collections.Iterable");
    NUMPY_ALIAS_TO_REAL_TYPE.put("tuple", "collections.Iterable");

    NUMPY_ALIAS_TO_REAL_TYPE.put("ints", "int");
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull PyFunction function, @Nullable PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
    if (isApplicable(function)) {
      final PyExpression callee = callSite instanceof PyCallExpression ? ((PyCallExpression)callSite).getCallee() : null;
      final NumPyDocString docString = NumPyDocString.forFunction(function, callee);
      if (docString != null) {
        final List<NumPyDocStringParameter> returns = docString.getReturns();
        final PyPsiFacade facade = getPsiFacade(function);
        switch (returns.size()) {
          case 0:
            return null;
          case 1:
            // Function returns single value
            final String typeName = returns.get(0).getType();
            if (typeName != null) {
              final PyType genericType = getPsiFacade(function).parseTypeAnnotation("T", function);
              if (isUfuncType(function, typeName)) return genericType;
              return parseNumpyDocType(function, typeName);
            }
            return null;
          default:
            // Function returns a tuple
            final ArrayList<PyType> unionMembers = new ArrayList<PyType>();

            final List<PyType> members = new ArrayList<PyType>();

            for (int i = 0; i < returns.size(); i++) {
              NumPyDocStringParameter ret = returns.get(i);
              final String memberTypeName = ret.getType();
              final PyType returnType = memberTypeName != null ? parseNumpyDocType(function, memberTypeName) : null;
              final boolean isOptional = memberTypeName != null && memberTypeName.contains("optional");

              if (isOptional) {
                if (i != 0) {
                  if(members.size() > 1)
                    unionMembers.add(facade.createTupleType(members, function));
                   else
                    unionMembers.add(returnType);
                }
              }
              members.add(returnType);

              if (i == returns.size() - 1 && isOptional) {
                unionMembers.add(facade.createTupleType(members, function));
              }
            }
            if (unionMembers.isEmpty()) {
              return facade.createTupleType(members, function);
            }
            return facade.createUnionType(unionMembers);
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getParameterType(@NotNull PyNamedParameter parameter, @NotNull PyFunction function, @NotNull TypeEvalContext context) {
    if (isApplicable(function)) {
      final String name = parameter.getName();
      if (name != null) {
        final PyType type = getParameterType(function, name);
        if (type != null) {
          return Ref.create(type);
        }
      }
    }
    return null;
  }

  private static boolean isInsideNumPy(@NotNull PsiElement element) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return true;
    final PsiFile file = element.getContainingFile();

    if (file != null) {
      final PyPsiFacade facade = getPsiFacade(element);
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        final String name = facade.findShortestImportableName(virtualFile, element);
        return name != null && (name.startsWith("numpy.") || name.startsWith("matplotlib."));
      }
    }
    return false;
  }

  private static boolean isApplicable(@NotNull PsiElement element) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module != null){
      if (PyDocumentationSettings.getInstance(module).isNumpyFormat(element.getContainingFile())) {
        return true;
      }
    }

    return isInsideNumPy(element);
  }

  private static PyPsiFacade getPsiFacade(@NotNull PsiElement anchor) {
    return PyPsiFacade.getInstance(anchor.getProject());
  }

  @Nullable
  private static PyType parseSingleNumpyDocType(@NotNull PsiElement anchor, @NotNull String typeString) {
    final PyPsiFacade facade = getPsiFacade(anchor);
    final String realTypeName = getNumpyRealTypeName(typeString);
    PyType type = facade.parseTypeAnnotation(realTypeName, anchor);
    if (type != null) {
      return type;
    }

    type = facade.parseTypeAnnotation(typeString, anchor);
    if (type != null) {
      return type;
    }
    return getNominalType(anchor, typeString);
  }

  @NotNull
  private static String getNumpyRealTypeName(@NotNull String typeString) {
    final String realTypeName = NUMPY_ALIAS_TO_REAL_TYPE.get(typeString);
    if (realTypeName != null) {
      return realTypeName;
    }
    final List<String> typeSubStrings = StringUtil.split(typeString, " ");
    List<String> typeParts = new ArrayList<String>();
    for (String string : typeSubStrings) {
      final String type = NUMPY_ALIAS_TO_REAL_TYPE.get(string);
      typeParts.add(type != null ? type : string);
    }
    typeString = StringUtil.join(typeParts, " ");
    return typeString;
  }

  /**
   * Converts literal into type, e.g. -1 -> int, 'fro' -> str
   */
  @Nullable
  private static PyType getNominalType(@NotNull PsiElement anchor, @NotNull String typeString) {
    final PyExpressionCodeFragmentImpl codeFragment = new PyExpressionCodeFragmentImpl(anchor.getProject(), "dummy.py", typeString, false);
    final PsiElement element = codeFragment.getFirstChild();
    if (element instanceof PyExpressionStatement) {
      final PyExpression expression = ((PyExpressionStatement)element).getExpression();
      final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(anchor);
      if (expression instanceof PyStringLiteralExpression) {
        return builtinCache.getStrType();
      }
      if (expression instanceof PyNumericLiteralExpression) {
        return builtinCache.getIntType();
      }
    }
    return null;
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

  private static boolean isUfuncType(@NotNull PsiElement anchor, @NotNull final String typeString) {
    for (String typeName : NumPyDocString.getNumpyUnionType(typeString)) {
      if (anchor instanceof PyFunction && isInsideNumPy(anchor) && NumpyUfuncs.isUFunc(((PyFunction)anchor).getName()) &&
          ("array_like".equals(typeName) || "ndarray".equals(typeName))) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PyType getParameterType(@NotNull PyFunction function, @NotNull String parameterName) {
    final NumPyDocString docString = NumPyDocString.forFunction(function, function);
    if (docString != null) {
      NumPyDocStringParameter parameter = docString.getNamedParameter(parameterName);

      // If parameter name starts with "p_", and we failed to obtain it from the docstring,
      // try to obtain parameter named without such prefix.
      if (parameter == null && parameterName.startsWith("p_")) {
        parameter = docString.getNamedParameter(parameterName.substring(2));
      }
      if (parameter != null) {
        if (isUfuncType(function, parameter.getType())) {
          return getPsiFacade(function).parseTypeAnnotation("T <= numbers.Number or numpy.core.multiarray.ndarray or collections.Iterable", function);
        }
        final PyType numpyDocType = parseNumpyDocType(function, parameter.getType());
        if ("size".equals(parameterName)) {
          return getPsiFacade(function).createUnionType(Lists.newArrayList(numpyDocType, PyBuiltinCache.getInstance(function).getIntType()));
        }
        return numpyDocType;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    if (callable instanceof PyFunction) {
      final PyType type = getCallType((PyFunction)callable, null, context);
      if (type != null) {
        return Ref.create(type);
      }
    }
    return null;
  }
}
