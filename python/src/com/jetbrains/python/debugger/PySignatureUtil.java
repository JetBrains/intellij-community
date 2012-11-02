package com.jetbrains.python.debugger;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParser;
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
      return pyType.getName();
    }
    else {
      return type;
    }
  }
}
