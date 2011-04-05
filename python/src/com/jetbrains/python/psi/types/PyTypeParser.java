package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyTypeParser {
  private PyTypeParser() {
  }

  @Nullable
  public static PyType getTypeByName(PsiElement anchor, String type) {
    if (type == null) {
      return null;
    }
    final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(anchor);

    if (type.equals("string")) {
      return builtinCache.getStrType();
    }
    if (type.equals("file object")) {
      return builtinCache.getObjectType("file");
    }
    if (type.equals("dictionary")) {
      return builtinCache.getObjectType("dict");
    }
    if (type.startsWith("list of")) {
      return builtinCache.getObjectType("list");
    }
    if (type.equals("integer")) {
      return builtinCache.getIntType();
    }
    final PyClassType classType = builtinCache.getObjectType(type);
    if (classType != null) {
      return classType;
    }
    return null;
  }
}
