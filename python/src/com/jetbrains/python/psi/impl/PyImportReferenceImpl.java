package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.RatedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
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
    // complete to possible modules
    return ResolveImportUtil.suggestImportVariants(myElement);
  }
}
