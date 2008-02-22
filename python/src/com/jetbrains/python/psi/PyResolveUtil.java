/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 14.06.2005
 * Time: 23:45:32
 * To change this template use File | Settings | File Templates.
 */
public class PyResolveUtil {
  private PyResolveUtil() {
  }

  @Nullable
  public static PsiElement treeWalkUp(PsiScopeProcessor processor, PsiElement elt, PsiElement lastParent, PsiElement place) {
    if (elt == null) return null;

    PsiElement cur = elt;
    do {
      if (!cur.processDeclarations(processor, ResolveState.initial(), cur == elt ? lastParent : null, elt)) {
        if (processor instanceof ResolveProcessor) {
          return ((ResolveProcessor)processor).getResult();
        }
      }
      if (cur instanceof PsiFile) break;
      cur = cur.getPrevSibling();
    }
    while (cur != null);

    if (elt == place) return null;

    return treeWalkUp(processor, elt.getContext(), elt, place);
  }

  public static class ResolveProcessor implements PsiScopeProcessor {
    private String _name;
    private PsiElement _result = null;

    public ResolveProcessor(final String name) {
      _name = name;
    }

    public PsiElement getResult() {
      return _result;
    }

    public boolean execute(PsiElement element, ResolveState substitutor) {
      if (element instanceof PsiNamedElement) {
        if (_name.equals(((PsiNamedElement)element).getName())) {
          _result = element;
          return false;
        }
      }
      else if (element instanceof PyReferenceExpression) {
        PyReferenceExpression expr = (PyReferenceExpression)element;
        String referencedName = expr.getReferencedName();
        if (referencedName != null && referencedName.equals(_name)) {
          _result = element;
          return false;
        }
      }

      return true;
    }

    @Nullable
    public <T> T getHint(Class<T> hintClass) {
      return null;
    }

    public void handleEvent(Event event, Object associated) {
    }
  }

  public static class MultiResolveProcessor implements PsiScopeProcessor {
    private String _name;
    private List<ResolveResult> _results = new ArrayList<ResolveResult>();

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

    public <T> T getHint(Class<T> hintClass) {
      return null;
    }

    public void handleEvent(Event event, Object associated) {
    }
  }

  public static class VariantsProcessor implements PsiScopeProcessor {
    private List<LookupElement> _names = new ArrayList<LookupElement>();

    public LookupElement[] getResult() {
      return _names.toArray(new LookupElement[_names.size()]);
    }

    public boolean execute(PsiElement element, ResolveState substitutor) {
      if (element instanceof PsiNamedElement) {
        _names.add(LookupElementFactory.getInstance().createLookupElement((PsiNamedElement) element));
      }
      else if (element instanceof PyReferenceExpression) {
        PyReferenceExpression expr = (PyReferenceExpression)element;
        String referencedName = expr.getReferencedName();
        if (referencedName != null) {
          _names.add(LookupElementFactory.getInstance().createLookupElement(element, referencedName));
        }
      }

      return true;
    }

    @Nullable
    public <T> T getHint(Class<T> hintClass) {
      return null;
    }

    public void handleEvent(Event event, Object associated) {
    }
  }
}
