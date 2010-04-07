package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.RatedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class PyImportReferenceImpl extends PyReferenceImpl {
  public PyImportReferenceImpl(PyReferenceExpressionImpl element) {
    super(element);
  }

  @NotNull
  @Override
  protected List<RatedResolveResult> resolveInner() {
    ResultList ret = new ResultList();

    final String referencedName = myElement.getReferencedName();
    if (referencedName == null) return ret;

    PsiElement target = ResolveImportUtil.resolveImportReference(myElement);

    target = PyUtil.turnDirIntoInit(target);
    if (target == null) {
      ret.clear();
      return ret; // it was a dir without __init__.py, worthless
    }
    ret.poke(target, RatedResolveResult.RATE_HIGH);
    return ret;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    PyExpression qualifier = myElement.getQualifier();
    if (qualifier != null) {
      // qualifier's type must be module, it should know how to complete
      PyType type = qualifier.getType();
      if (type != null) return type.getCompletionVariants(myElement, new ProcessingContext());
      else return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    else {
      // complete to possible modules
      return ResolveImportUtil.suggestImportVariants(myElement);
    }
  }
}
