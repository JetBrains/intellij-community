package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyResolveUtil;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author yole
 */
public class PyClassType implements PyType {

  protected PyClass myClass;
  
  public PyClassType(final PyClass source) {
    myClass = source;
  }

  public PyClass getPyClass() {
    return myClass;
  }

  @Nullable
  public PsiElement resolveMember(final String name) {
    if (myClass == null) return null;
    PyResolveUtil.ResolveProcessor processor = new PyResolveUtil.ResolveProcessor(name);
    myClass.processDeclarations(processor, ResolveState.initial(), null, myClass); // our members are strictly within us.
    final PsiElement resolveResult = processor.getResult();
    //final PsiElement resolveResult = PyResolveUtil.treeWalkUp(new PyResolveUtil.ResolveProcessor(name), myClass, null, null);
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
  
  @NotNull
  public Set<String> getPossibleInstanceMembers() {
    Set<String> ret = new HashSet<String>();
    if (myClass != null) {
      PyClassType otype = PyBuiltinCache.getInstance(myClass.getProject()).getObjectType();
      ret.addAll(otype.getPossibleInstanceMembers());
    }
    // TODO: add our own ideas here, e.g. from methods other than constructor 
    return Collections.unmodifiableSet(ret); 
  }
  
}
