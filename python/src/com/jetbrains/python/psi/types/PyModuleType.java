package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveState;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyResolveUtil;
import com.jetbrains.python.psi.impl.ResolveImportUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
// .impl looks impure

/**
 * @author yole
 */
public class PyModuleType implements PyType {
  private PsiFile myModule;

  protected static Set<String> ourPossibleFields;
  static {
    ourPossibleFields = new HashSet<String>();
    ourPossibleFields.addAll(PyObjectType.ourPossibleFields);
    ourPossibleFields.add("__name__");
    ourPossibleFields.add("__file__");
    ourPossibleFields.add("__path__");
    ourPossibleFields = Collections.unmodifiableSet(ourPossibleFields); 
  }

  public PyModuleType(PsiFile source) {
    myModule = source;
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

  @NotNull
  public Set<String> getPossibleInstanceMembers() {
    return ourPossibleFields;
  }
  
}
