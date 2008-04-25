package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyResolveUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyClassType implements PyType {
  private PyClass myClass;

  public PyClassType(@NotNull final PyClass aClass) {
    myClass = aClass;
  }

  public PyClass getPyClass() {
    return myClass;
  }

  @Nullable
  public PsiElement resolveMember(final String name) {
    if (myClass == null) return null;
    final PsiElement resolveResult = PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(name), myClass, null, null);
    if (resolveResult != null) {
      return resolveResult;
    }
    final PyExpression[] superClassExpressions = myClass.getSuperClassExpressions();
    if (superClassExpressions != null) {
      for(PyExpression expr: superClassExpressions) {
        PyType superType = expr.getType();
        if (superType != null) {
          PsiElement superMember = superType.resolveMember(name);
          if (superMember != null) {
            return superMember;
          }
        }
      }
    }
    return null;
  }

  public Object[] getCompletionVariants(final PyReferenceExpression referenceExpression) {
    final PyResolveUtil.VariantsProcessor processor = new PyResolveUtil.VariantsProcessor();
    myClass.processDeclarations(processor, ResolveState.initial(), null, referenceExpression);
    return processor.getResult();
  }
}
