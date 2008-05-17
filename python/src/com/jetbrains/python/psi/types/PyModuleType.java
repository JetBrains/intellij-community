package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.jetbrains.python.psi.PyResolveUtil;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.impl.ResolveImportUtil; // .impl looks impure

/**
 * @author yole
 */
public class PyModuleType implements PyType {
  private PsiFile myModule;

  public PyModuleType(final PsiFile module) {
    myModule = module;
  }

  public PsiFile getModule() {
    return myModule;
  }

  public PsiElement resolveMember(final String name) {
    //return PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(name), myModule, null, null);
    return ResolveImportUtil.resolveChild(myModule, name, null);
  }

  public Object[] getCompletionVariants(final PyReferenceExpression referenceExpression) {
    final PyResolveUtil.VariantsProcessor processor = new PyResolveUtil.VariantsProcessor();
    myModule.processDeclarations(processor, ResolveState.initial(), null, referenceExpression);
    return processor.getResult();
  }
}
