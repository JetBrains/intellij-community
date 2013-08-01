package com.jetbrains.python.debugger;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParser;
import com.jetbrains.python.psi.types.PyUnionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class PySignatureUtil {
  private PySignatureUtil() {
  }

  @Nullable
  public static String getShortestImportableName(@Nullable PsiElement anchor, @NotNull String type) {
    final PyType pyType = PyTypeParser.getTypeByName(anchor, type);
    if (pyType instanceof PyClassType) {
      PyClass c = ((PyClassType)pyType).getPyClass();
      return c.getQualifiedName();
    }

    if (pyType != null) {
      return getPrintableName(pyType);
    }
    else {
      return type;
    }
  }

  private static String getPrintableName(PyType type) {
    if (type instanceof PyUnionType) {
      return StringUtil.join(Collections2.transform(((PyUnionType)type).getMembers(), new Function<PyType, String>() {
        @Override
        public String apply(@Nullable PyType input) {
          return getPrintableName(input);
        }
      }), " or ");
    }
    else if (type != null) {
      return type.getName();
    }
    else {
      return PyNames.UNKNOWN_TYPE;
    }
  }

  @Nullable
  public static String getArgumentType(@NotNull PyFunction function, @NotNull String name) {
    PySignatureCacheManager cacheManager = PySignatureCacheManager.getInstance(function.getProject());
    PySignature signature = cacheManager.findSignature(function);
    if (signature != null) {
      return getShortestImportableName(function, signature.getArgTypeQualifiedName(name));
    }
    return null;
  }
}
