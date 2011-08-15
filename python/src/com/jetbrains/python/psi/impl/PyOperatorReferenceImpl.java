package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author vlan
 */
public class PyOperatorReferenceImpl extends PyReferenceImpl {
  public PyOperatorReferenceImpl(PyQualifiedExpression element, @NotNull PyResolveContext context) {
    super(element, context);
  }

  @NotNull
  @Override
  protected List<RatedResolveResult> resolveInner() {
    List<RatedResolveResult> res = new ArrayList<RatedResolveResult>();
    if (myElement instanceof PyBinaryExpression) {
      final PyBinaryExpression expr = (PyBinaryExpression)myElement;
      final String name = expr.getReferencedName();
      if (PyNames.CONTAINS.equals(name)) {
        res = resolveMember(expr.getRightExpression(), name);
      }
      else {
        res = resolveMember(expr.getLeftExpression(), name);
        if (res.isEmpty()) {
          res = resolveMember(expr.getRightExpression(), leftToRightOperatorName(name));
        }
      }
    }
    else if (myElement instanceof PySubscriptionExpression) {
      final PySubscriptionExpression expr = (PySubscriptionExpression)myElement;
      res = resolveMember(expr.getOperand(), expr.getReferencedName());
    }
    return res;
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PyParameter || element instanceof PyTargetExpression) {
      return false;
    }
    return super.isReferenceTo(element);
  }

  @Override
  public String toString() {
    return "PyOperatorReferenceImpl(" + myElement + "," + myContext + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PyOperatorReferenceImpl other = (PyOperatorReferenceImpl)o;
    return myElement.equals(other.myElement) && myContext.equals(myContext);
  }

  @Override
  public int hashCode() {
    return myElement.hashCode();
  }

  public String getReadableOperatorName() {
    final String name = myElement.getReferencedName();
    if (PyNames.SUBCRIPTION_OPERATORS.contains(name)) {
      return "[]";
    }
    else {
      return getRangeInElement().substring(myElement.getText());
    }
  }

  private static String leftToRightOperatorName(String name) {
    return name.replaceFirst("__([a-z]+)__", "__r$1__");
  }

  @NotNull
  private List<RatedResolveResult> resolveMember(@Nullable PyExpression object, @Nullable String name) {
    final ArrayList<RatedResolveResult> results = new ArrayList<RatedResolveResult>();
    if (object != null && name != null) {
      final PyType type = myContext.getTypeEvalContext().getType(object);
      if (type != null && !(type instanceof PyTypeReference)) {
        List<? extends RatedResolveResult> res = type.resolveMember(name, object, AccessDirection.of(myElement), myContext);
        if (res != null) {
          results.addAll(res);
        }
      }
    }
    return results;
  }
}
