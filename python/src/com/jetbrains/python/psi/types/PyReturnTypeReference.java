package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyReturnTypeReference extends PyTypeReferenceImpl {
  private final PyFunction myFunction;

  public PyReturnTypeReference(PyFunction function) {
    myFunction = function;
  }

  @Nullable
  public PyType resolve(PsiElement context) {
    return myFunction.getReturnType();
  }

  public String getName() {
    return "return type of " + myFunction.getPresentation().getPresentableText(); 
  }
}
