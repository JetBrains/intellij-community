package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.psi.PyReferenceExpression;

import java.util.ArrayList;
import java.util.List;

public class MultiResolveProcessor implements PsiScopeProcessor {
  private final String _name;
  private final List<ResolveResult> _results = new ArrayList<ResolveResult>();

  public MultiResolveProcessor(String name) {
    _name = name;
  }

  public ResolveResult[] getResults() {
    return _results.toArray(new ResolveResult[_results.size()]);
  }

  public boolean execute(PsiElement element, ResolveState substitutor) {
    if (element instanceof PsiNamedElement) {
      if (_name.equals(((PsiNamedElement)element).getName())) {
        _results.add(new PsiElementResolveResult(element));
      }
    }
    else if (element instanceof PyReferenceExpression) {
      PyReferenceExpression expr = (PyReferenceExpression)element;
      String referencedName = expr.getReferencedName();
      if (referencedName != null && referencedName.equals(_name)) {
        _results.add(new PsiElementResolveResult(element));
      }
    }

    return true;
  }

  public <T> T getHint(Key<T> hintKey) {
    return null;
  }

  public void handleEvent(Event event, Object associated) {
  }
}
