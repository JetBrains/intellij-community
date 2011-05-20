package com.jetbrains.python.documentation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public class DocStringTypeReference extends PsiReferenceBase<PsiElement> {
  PyType myType;

  public DocStringTypeReference(PsiElement element, TextRange range, PyType type) {
    super(element, range);
    myType = type;
  }

  public boolean isSoft() {
    return true;
  }

  @Nullable
  public PsiElement resolve() {
    if (myType instanceof PyClassType) {
      return ((PyClassType)myType).getPyClass();
    }
    return null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return new Object[]{"str", "int", "basestring", "bool", "buffer", "bytearray", "complex", "dict", "tuple", "enumerate",
      "file", "float", "frozenset", "list", "long", "set", "object"};
  }
}
