/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyKeywordArgumentProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.psi.PyUtil.as;

public class KeywordArgumentCompletionUtil {
  public static void collectFunctionArgNames(PyElement element, List<LookupElement> ret, @NotNull final TypeEvalContext context) {
    PyCallExpression callExpr = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
    if (callExpr != null) {
      PyExpression callee = callExpr.getCallee();
      if (callee instanceof PyReferenceExpression && element.getParent() == callExpr.getArgumentList()) {
        PyType calleeType = context.getType(callee);
        if (calleeType == null) {
          final PyTypedElement implicit = as(getElementByChain((PyReferenceExpression)callee, context), PyTypedElement.class);
          if (implicit != null) {
            calleeType = context.getType(implicit);
          }
        }
        final List<LookupElement> extra = PyTypeUtil.toStream(calleeType)
          .select(PyCallableType.class)
          .flatMap(type -> collectParameterNamesFromType(type, callExpr, context).stream())
          .map(name -> PyUtil.createNamedParameterLookup(name, element.getProject()))
          .toList();

        ret.addAll(extra);
      }
    }
  }

  @NotNull
  private static List<String> collectParameterNamesFromType(@NotNull PyCallableType type,
                                                            @NotNull PyCallExpression callSite,
                                                            @NotNull TypeEvalContext context) {
    List<String> result = new ArrayList<>();
    if (type.isCallable()) {
      final List<PyCallableParameter> parameters = type.getParameters(context);
      if (parameters != null) {
        for (PyCallableParameter parameter : parameters) {
          if (parameter.isKeywordContainer() || parameter.isPositionalContainer()) {
            continue;
          }
          ContainerUtil.addIfNotNull(result, parameter.getName());
        }
        PyFunction func = null;
        if (type instanceof PyFunctionType) {
          func = as(((PyFunctionType)type).getCallable(), PyFunction.class);
        }
        else if (type instanceof PyClassType) {
          func = ((PyClassType)type).getPyClass().findInitOrNew(true, context);
        }
        if (func != null) {
          addKeywordArgumentVariantsForFunction(callSite, func, result, new HashSet<>(), context);
        }
      }
    }
    return result;
  }

  private static PsiElement getElementByChain(@NotNull PyReferenceExpression callee, @NotNull TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);
    final QualifiedResolveResult result = callee.followAssignmentsChain(resolveContext);
    return result.getElement();
  }

  private static void addKeywordArgumentVariantsForFunction(@NotNull final PyCallExpression callExpr,
                                                            @NotNull final PyFunction function,
                                                            @NotNull final List<String> ret,
                                                            @NotNull final Set<PyCallable> visited,
                                                            @NotNull final TypeEvalContext context) {
    if (visited.contains(function)) {
      return;
    }

    boolean needSelf = function.getContainingClass() != null && function.getModifier() != PyFunction.Modifier.STATICMETHOD;
    final KwArgParameterCollector collector = new KwArgParameterCollector(needSelf, ret);

    StreamEx
      .of(function.getParameters(context))
      .map(PyCallableParameter::getParameter)
      .nonNull()
      .forEach(parameter -> parameter.accept(collector));

    if (collector.hasKwArgs()) {
      for (PyKeywordArgumentProvider provider : Extensions.getExtensions(PyKeywordArgumentProvider.EP_NAME)) {
        ret.addAll(provider.getKeywordArguments(function, callExpr));
      }
      KwArgFromStatementCallCollector fromStatementCallCollector = new KwArgFromStatementCallCollector(ret, collector.getKwArgs());
      function.getStatementList().acceptChildren(fromStatementCallCollector);

      //if (collector.hasOnlySelfAndKwArgs()) {
      // nothing interesting besides self and **kwargs, let's look at superclass (PY-778)
      if (fromStatementCallCollector.isKwArgsTransit()) {

        final PyFunction superMethod = as(PySuperMethodsSearch.search(function, context).findFirst(), PyFunction.class);
        if (superMethod != null) {
          addKeywordArgumentVariantsForFunction(callExpr, superMethod, ret, visited, context);
        }
      }
    }
  }

  public static class KwArgParameterCollector extends PyElementVisitor {
    private int myCount;
    private final boolean myNeedSelf;
    private final List<String> myRet;
    private boolean myHasSelf = false;
    private boolean myHasKwArgs = false;
    private PyParameter kwArgsParam = null;

    public KwArgParameterCollector(boolean needSelf, List<String> ret) {
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
          myRet.add(namedParam.getName());
        }
        else if (namedParam.isKeywordContainer()) {
          myHasKwArgs = true;
          kwArgsParam = namedParam;
        }
      }
    }

    public PyParameter getKwArgs() {
      return kwArgsParam;
    }

    public boolean hasKwArgs() {
      return myHasKwArgs;
    }
  }

  public static class KwArgFromStatementCallCollector extends PyElementVisitor {
    private final List<String> myRet;
    private final PyParameter myKwArgs;
    private boolean kwArgsTransit = true;

    public KwArgFromStatementCallCollector(List<String> ret, @NotNull PyParameter kwArgs) {
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
        if (PyNames.isIdentifier(name)) {
          myRet.add(name);
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
