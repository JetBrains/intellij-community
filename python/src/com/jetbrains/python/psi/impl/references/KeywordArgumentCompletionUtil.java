/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.impl.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyKeywordArgumentProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class KeywordArgumentCompletionUtil {
  public static void collectFunctionArgNames(PyElement element, List<LookupElement> ret) {
    PyCallExpression callExpr = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
    if (callExpr != null) {
      PyExpression callee = callExpr.getCallee();
      if (callee instanceof PyReferenceExpression && element.getParent() == callExpr.getArgumentList()) {
        final QualifiedResolveResult result = ((PyReferenceExpression)callee).followAssignmentsChain(PyResolveContext.defaultContext());
        PsiElement def = result.getElement();
        if (def instanceof PyFunction) {
          addKeywordArgumentVariants((PyFunction)def, callExpr, ret);
        }
        else if (def instanceof PyClass) {
          PyFunction init = ((PyClass)def).findMethodByName(PyNames.INIT, true);  // search in superclasses
          if (init != null) {
            addKeywordArgumentVariants(init, callExpr, ret);
          }
        }
      }
    }
  }

  public static void addKeywordArgumentVariants(PyFunction def, PyCallExpression callExpr, final List<LookupElement> ret) {
    addKeywordArgumentVariants(def, callExpr, ret, new HashSet<PyFunction>());
  }

  public static void addKeywordArgumentVariants(PyFunction def, PyCallExpression callExpr, List<LookupElement> ret,
                                                Collection<PyFunction> visited) {
    if (visited.contains(def)) {
      return;
    }
    visited.add(def);
    boolean needSelf = def.getContainingClass() != null && def.getModifier() != PyFunction.Modifier.STATICMETHOD;
    final KwArgParameterCollector collector = new KwArgParameterCollector(needSelf, ret);
    final TypeEvalContext context = TypeEvalContext.userInitiated(def.getContainingFile());
    final List<PyParameter> parameters = PyUtil.getParameters(def, context);
    for (PyParameter parameter : parameters) {
      parameter.accept(collector);
    }
    if (collector.hasKwArgs()) {
      for (PyKeywordArgumentProvider provider : Extensions.getExtensions(PyKeywordArgumentProvider.EP_NAME)) {
        final List<String> arguments = provider.getKeywordArguments(def, callExpr);
        for (String argument : arguments) {
          ret.add(PyUtil.createNamedParameterLookup(argument));
        }
      }
      KwArgFromStatementCallCollector fromStatementCallCollector = new KwArgFromStatementCallCollector(ret, collector.getKwArgs());
      final PyStatementList statementList = def.getStatementList();
      if (statementList != null)
        statementList.acceptChildren(fromStatementCallCollector);

      //if (collector.hasOnlySelfAndKwArgs()) {
      // nothing interesting besides self and **kwargs, let's look at superclass (PY-778)
      if (fromStatementCallCollector.isKwArgsTransit()) {

        final PsiElement superMethod = PySuperMethodsSearch.search(def).findFirst();
        if (superMethod instanceof PyFunction) {
          addKeywordArgumentVariants((PyFunction)superMethod, callExpr, ret, visited);
        }
      }
    }
//}
  }

  public static class KwArgParameterCollector extends PyElementVisitor {
    private int myCount;
    private final boolean myNeedSelf;
    private final List<LookupElement> myRet;
    private boolean myHasSelf = false;
    private boolean myHasKwArgs = false;
    private PyParameter kwArgsParam = null;

    public KwArgParameterCollector(boolean needSelf, List<LookupElement> ret) {
      myNeedSelf = needSelf;
      myRet = ret;
    }

    @Override
    public void visitPyParameter(PyParameter par) {
      myCount++;
      if (myCount == 1 && myNeedSelf) {
        myHasSelf = true;
        return;
      }
      PyNamedParameter namedParam = par.getAsNamed();
      if (namedParam != null) {
        if (!namedParam.isKeywordContainer() && !namedParam.isPositionalContainer()) {
          final LookupElement item = PyUtil.createNamedParameterLookup(namedParam.getName());
          myRet.add(item);
        }
        else if (namedParam.isKeywordContainer()) {
          myHasKwArgs = true;
          kwArgsParam = namedParam;
        }
      }
      else {
        PyTupleParameter nestedTParam = par.getAsTuple();
        if (nestedTParam != null) {
          for (PyParameter inner_par : nestedTParam.getContents()) inner_par.accept(this);
        }
        // else it's a lone star that can't contribute
      }
    }

    public PyParameter getKwArgs() {
      return kwArgsParam;
    }

    public boolean hasKwArgs() {
      return myHasKwArgs;
    }

    public boolean hasOnlySelfAndKwArgs() {
      return myCount == 2 && myHasSelf && myHasKwArgs;
    }
  }

  public static class KwArgFromStatementCallCollector extends PyElementVisitor {
    private final List<LookupElement> myRet;
    private final PyParameter myKwArgs;
    private boolean kwArgsTransit = true;

    public KwArgFromStatementCallCollector(List<LookupElement> ret, @NotNull PyParameter kwArgs) {
      myRet = ret;
      this.myKwArgs = kwArgs;
    }

    @Override
    public void visitPyElement(PyElement node) {
      node.acceptChildren(this);
    }

    @Override
    public void visitPySubscriptionExpression(PySubscriptionExpression node) {
      String operandName = node.getOperand().getName();
      processGet(operandName, node.getIndexExpression());
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      if (node.isCalleeText("pop", "get", "getattr")) {
        PyReferenceExpression child = PsiTreeUtil.getChildOfType(node.getCallee(), PyReferenceExpression.class);
        if (child != null) {
          String operandName = child.getName();
          if (node.getArguments().length > 0) {
            PyExpression argument = node.getArguments()[0];
            processGet(operandName, argument);
          }
        }
      }
      else if (node.isCalleeText("__init__")) {
        kwArgsTransit = false;
        for (PyExpression e : node.getArguments()) {
          if (e instanceof PyStarArgument) {
            PyStarArgument kw = (PyStarArgument)e;
            if (Comparing.equal(myKwArgs.getName(), kw.getFirstChild().getNextSibling().getText())) {
              kwArgsTransit = true;
              break;
            }
          }
        }
      }
      super.visitPyCallExpression(node);
    }

    private void processGet(String operandName, PyExpression argument) {
      if (Comparing.equal(myKwArgs.getName(), operandName) &&
          argument instanceof PyStringLiteralExpression) {
        String name = ((PyStringLiteralExpression)argument).getStringValue();
        if (PyUtil.isPythonIdentifier(name)) {
          myRet.add(PyUtil.createNamedParameterLookup(name));
        }
      }
    }

    /**
     * is name of kwargs parameter the same as transmitted to __init__ call
     *
     * @return
     */
    public boolean isKwArgsTransit() {
      return kwArgsTransit;
    }
  }
}
