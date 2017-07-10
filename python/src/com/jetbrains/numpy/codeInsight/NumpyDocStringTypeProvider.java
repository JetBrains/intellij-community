/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.NumpyDocString;
import com.jetbrains.python.documentation.docstrings.SectionBasedDocString.SectionField;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl;
import com.jetbrains.python.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides type information extracted from NumPy docstring format.
 *
 * @author avereshchagin
 * @author vlan
 */
public class NumpyDocStringTypeProvider extends PyTypeProviderBase {
  private static final Map<String, String> NUMPY_ALIAS_TO_REAL_TYPE = new HashMap<>();
  private static final Pattern REDIRECT = Pattern.compile("^Refer to `(.*)` for full documentation.$");
  private static final Pattern NUMPY_UNION_PATTERN = Pattern.compile("^\\{(.*)\\}$");
  private static final Pattern NUMPY_ARRAY_PATTERN = Pattern.compile("(\\(\\.\\.\\..*\\))(.*)");
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
    NUMPY_ALIAS_TO_REAL_TYPE.put("non-zero int", "int");
  }

  @Nullable
  private static NumpyDocString forFunction(@NotNull PyFunction function, @Nullable PsiElement reference, @Nullable String knownSignature) {
    String docString = function.getDocStringValue();
    if (docString == null && PyNames.INIT.equals(function.getName())) {
      // Docstring for constructor can be found in the docstring of class
      PyClass cls = function.getContainingClass();
      if (cls != null) {
        docString = cls.getDocStringValue();
      }
    }

    if (docString != null) {
      final NumpyDocString parsed = (NumpyDocString)DocStringUtil.parseDocStringContent(DocStringFormat.NUMPY, docString);
      if (parsed.getReturnFields().isEmpty() && parsed.getParameterFields().isEmpty()) {
        return null;
      }

      String signature = parsed.getSignature();
      String redirect = findRedirect(parsed.getLines());
      if (redirect != null && reference != null) {
        PyFunction resolvedFunction = resolveRedirectToFunction(redirect, reference);
        if (resolvedFunction != null) {
          return forFunction(resolvedFunction, reference, knownSignature != null ? knownSignature : signature);
        }
      }
      return parsed;
    }
    return null;
  }

  /**
   * Returns NumPyDocString object confirming to Numpy-style formatted docstring of specified function.
   *
   * @param function  Function containing docstring for which Numpy wrapper object is to be obtained.
   * @param reference An original reference element to specified function.
   * @return Numpy docstring wrapper object for specified function.
   */
  @Nullable
  public static NumpyDocString forFunction(@NotNull PyFunction function, @Nullable PsiElement reference) {
    return forFunction(function, reference, null);
  }

  @Nullable
  private static String findRedirect(@NotNull List<Substring> lines) {
    for (Substring line : lines) {
      Matcher matcher = REDIRECT.matcher(line);
      if (matcher.matches() && matcher.groupCount() > 0) {
        return matcher.group(1);
      }
    }
    return null;
  }

  /**
   * Returns PyFunction object for specified fully qualified name accessible from specified reference.
   *
   * @param redirect  A fully qualified name of function that is redirected to.
   * @param reference An original reference element.
   * @return Resolved function or null if it was not resolved.
   */
  @Nullable
  private static PyFunction resolveRedirectToFunction(@NotNull String redirect, @NotNull PsiElement reference) {
    final QualifiedName qualifiedName = QualifiedName.fromDottedString(redirect);
    final String functionName = qualifiedName.getLastComponent();
    final List<PsiElement> items = PyResolveImportUtil.resolveQualifiedName(qualifiedName.removeLastComponent(),
                                                                            PyResolveImportUtil.fromFoothold(reference));
    for (PsiElement item : items) {
      if (item instanceof PsiDirectory) {
        item = ((PsiDirectory)item).findFile(PyNames.INIT_DOT_PY);
      }
      if (item instanceof PyFile) {
        final PsiElement element = ((PyFile)item).getElementNamed(functionName);
        if (element instanceof PyFunction) {
          return (PyFunction)element;
        }
      }
    }
    return null;
  }

  @Nullable
  public static String cleanupOptional(@NotNull String typeString) {
    int index = typeString.indexOf(", optional");
    if (index >= 0) {
      return typeString.substring(0, index);
    }
    return null;
  }

  @NotNull
  public static List<String> getNumpyUnionType(@NotNull String typeString) {
    final Matcher arrayMatcher = NUMPY_ARRAY_PATTERN.matcher(typeString);
    if (arrayMatcher.matches()) {
      typeString = arrayMatcher.group(2);
    }
    Matcher matcher = NUMPY_UNION_PATTERN.matcher(typeString);
    if (matcher.matches()) {
      typeString = matcher.group(1);
    }
    return Arrays.asList(typeString.split(" *, *"));
  }

  @Nullable
  @Override
  public Ref<PyType> getCallType(@NotNull PyFunction function, @Nullable PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
    if (isApplicable(function)) {
      final PyExpression callee = callSite instanceof PyCallExpression ? ((PyCallExpression)callSite).getCallee() : null;
      final NumpyDocString docString = forFunction(function, callee);
      if (docString != null) {
        final List<SectionField> returns = docString.getReturnFields();
        final PyPsiFacade facade = getPsiFacade(function);

        switch (returns.size()) {
          case 0:
            return null;
          case 1:
            // Function returns single value
            return Optional
              .ofNullable(returns.get(0).getType())
              .filter(StringUtil::isNotEmpty)
              .map(typeName -> isUfuncType(function, typeName)
                               ? facade.parseTypeAnnotation("T", function)
                               : parseNumpyDocType(function, typeName))
              .map(Ref::create)
              .orElse(null);
          default:
            // Function returns a tuple
            final List<PyType> unionMembers = new ArrayList<>();
            final List<PyType> members = new ArrayList<>();

            for (int i = 0; i < returns.size(); i++) {
              final String memberTypeName = returns.get(i).getType();
              final PyType returnType = StringUtil.isNotEmpty(memberTypeName) ? parseNumpyDocType(function, memberTypeName) : null;
              final boolean isOptional = StringUtil.isNotEmpty(memberTypeName) && memberTypeName.contains("optional");

              if (isOptional && i != 0) {
                if (members.size() > 1) {
                  unionMembers.add(facade.createTupleType(members, function));
                }
                else if (members.size() == 1) {
                  unionMembers.add(members.get(0));
                }
              }
              members.add(returnType);

              if (i == returns.size() - 1 && isOptional) {
                unionMembers.add(facade.createTupleType(members, function));
              }
            }

            final PyType type = unionMembers.isEmpty() ? facade.createTupleType(members, function) : facade.createUnionType(unionMembers);
            return Ref.create(type);
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

  public static boolean isInsideNumPy(@NotNull PsiElement element) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return true;
    final PsiFile file = element.getContainingFile();

    if (file != null) {
      final PyPsiFacade facade = getPsiFacade(element);
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        final String name = facade.findShortestImportableName(virtualFile, element);
        return name != null && (name.startsWith("numpy.") || name.startsWith("matplotlib.") || name.startsWith("scipy."));
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
    List<String> typeParts = new ArrayList<>();
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
    final String withoutOptional = cleanupOptional(typeString);
    final Set<PyType> types = new LinkedHashSet<>();
    if (withoutOptional != null) {
      typeString = withoutOptional;
    }
    for (String typeName : getNumpyUnionType(typeString)) {
      PyType parsedType = parseSingleNumpyDocType(anchor, typeName);
      if (parsedType != null) {
        types.add(parsedType);
      }
    }
    if (!types.isEmpty() && withoutOptional != null) {
      types.add(PyNoneType.INSTANCE);
    }
    return getPsiFacade(anchor).createUnionType(types);
  }

  public static boolean isUfuncType(@NotNull PsiElement anchor, @NotNull final String typeString) {
    for (String typeName : getNumpyUnionType(typeString)) {
      if (anchor instanceof PyFunction && isInsideNumPy(anchor) && NumpyUfuncs.isUFunc(((PyFunction)anchor).getName()) &&
          ("array_like".equals(typeName) || "ndarray".equals(typeName))) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PyType getParameterType(@NotNull PyFunction function, @NotNull String parameterName) {
    final NumpyDocString docString = forFunction(function, function);
    if (docString != null) {
      String paramType = docString.getParamType(parameterName);

      // If parameter name starts with "p_", and we failed to obtain it from the docstring,
      // try to obtain parameter named without such prefix.
      if (paramType == null && parameterName.startsWith("p_")) {
        paramType = docString.getParamType(parameterName.substring(2));
      }
      if (paramType != null) {
        if (isUfuncType(function, paramType)) {
          return getPsiFacade(function).parseTypeAnnotation("numbers.Number or numpy.core.multiarray.ndarray or collections.Iterable", function);
        }
        final PyType numpyDocType = parseNumpyDocType(function, paramType);
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
    return Optional
      .ofNullable(PyUtil.as(callable, PyFunction.class))
      .map(function -> getCallType(function, null, context))
      .orElse(null);
  }
}
