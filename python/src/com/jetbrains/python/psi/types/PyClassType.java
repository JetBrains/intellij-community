package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyResolveUtil;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyClassType implements PyType {
  private PyClass myClass;

  public PyClassType(final PyClass aClass) {
    myClass = aClass;
  }

  public PyClass getPyClass() {
    return myClass;
  }

  @Nullable
  public PsiElement resolveMember(final String name) {
    return PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(name), myClass, null, null);
  }

  public Object[] getCompletionVariants(final PyReferenceExpression referenceExpression) {
    final PyResolveUtil.VariantsProcessor processor = new PyResolveUtil.VariantsProcessor();
    myClass.processDeclarations(processor, ResolveState.initial(), null, referenceExpression);
    return processor.getResult();
  }
}
