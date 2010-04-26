package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyFunction;

/**
 * @author yole
 */
public class PyReturnTypeReference extends PyTypeReferenceImpl {
  private PyFunction myFunction;

  public PyReturnTypeReference(PyFunction function) {
    myFunction = function;
  }

  public PyType resolve(PsiElement context) {
    return myFunction.getReturnType();
  }

  public String getName() {
    return "return type of " + myFunction.getPresentation().getPresentableText(); 
  }
}
