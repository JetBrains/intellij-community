/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.jetbrains.python.codeInsight.stdlib.PyNamedTupleType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyKeywordArgumentProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class KeywordArgumentCompletionUtil {
  public static void collectFunctionArgNames(PyElement element, List<LookupElement> ret, @NotNull final TypeEvalContext context) {
    PyCallExpression callExpr = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
    if (callExpr != null) {
      PyExpression callee = callExpr.getCallee();
      if (callee instanceof PyReferenceExpression && element.getParent() == callExpr.getArgumentList()) {
        PsiElement def = getElementByType(context, callee);
        if (def == null) {
          def = getElementByChain(context, (PyReferenceExpression)callee);
        }

        if (def instanceof PyCallable) {
          addKeywordArgumentVariants((PyCallable)def, callExpr, ret);
        }
        else if (def instanceof PyClass) {
          PyFunction init = ((PyClass)def).findMethodByName(PyNames.INIT, true, null);  // search in superclasses
          if (init != null) {
            addKeywordArgumentVariants(init, callExpr, ret);
          }
        }

        final PyType calleeType = context.getType(callee);

        final PyUnionType unionType = PyUtil.as(calleeType, PyUnionType.class);
        if (unionType != null) {
          fetchCallablesFromUnion(ret, callExpr, unionType, context);
        }

        final PyNamedTupleType namedTupleType = PyUtil.as(calleeType, PyNamedTupleType.class);
        if (namedTupleType != null) {
          for (String name : namedTupleType.getElementNames()) {
            ret.add(
              PyUtil.createNamedParameterLookup(name, element.getProject())
            );
          }
        }
      }
    }
  }

  @Nullable
  private static PyElement getElementByType(@NotNull final TypeEvalContext context, @NotNull final PyExpression callee) {
    final PyType pyType = context.getType(callee);
    if (pyType instanceof PyFunctionType) {
      return ((PyFunctionType)pyType).getCallable();
    }
    if (pyType instanceof PyClassType) {
      return ((PyClassType)pyType).getPyClass();
    }
    return null;
  }

  private static PsiElement getElementByChain(@NotNull TypeEvalContext context, PyReferenceExpression callee) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);
    final QualifiedResolveResult result = callee.followAssignmentsChain(resolveContext);
    return result.getElement();
  }

  private static void fetchCallablesFromUnion(@NotNull final List<LookupElement> ret,
                                              @NotNull final PyCallExpression callExpr,
                                              @NotNull final PyUnionType unionType,
                                              @NotNull final TypeEvalContext context) {
    for (final PyType memberType : unionType.getMembers()) {
      if (memberType instanceof PyUnionType) {
        fetchCallablesFromUnion(ret, callExpr, (PyUnionType)memberType, context);
      }
      if (memberType instanceof PyFunctionType) {
        final PyFunctionType type = (PyFunctionType)memberType;
        if (type.isCallable()) {
          addKeywordArgumentVariants(type.getCallable(), callExpr, ret);
        }
      }
      if (memberType instanceof PyCallableType) {
        final List<PyCallableParameter> callableParameters = ((PyCallableType)memberType).getParameters(context);
        if (callableParameters != null) {
          fetchCallablesFromCallableType(ret, callExpr, callableParameters);
        }
      }
    }
  }

  private static void fetchCallablesFromCallableType(@NotNull final List<LookupElement> ret,
                                                     @NotNull final PyCallExpression callExpr,
                                                     @NotNull final Iterable<PyCallableParameter> callableParameters) {
    final List<String> parameterNames = new ArrayList<>();
    for (final PyCallableParameter callableParameter : callableParameters) {
      final String name = callableParameter.getName();
      if (name != null) {
        parameterNames.add(name);
      }
    }
    addKeywordArgumentVariantsForCallable(callExpr, ret, parameterNames);
  }

  public static void addKeywordArgumentVariants(PyCallable callable, PyCallExpression callExpr, final List<LookupElement> ret) {
    addKeywordArgumentVariants(callable, callExpr, ret, new HashSet<>());
  }

  public static void addKeywordArgumentVariants(PyCallable callable, PyCallExpression callExpr, List<LookupElement> ret,
                                                Collection<PyCallable> visited) {
    if (visited.contains(callable)) {
      return;
    }
    visited.add(callable);

    final TypeEvalContext context = TypeEvalContext.codeCompletion(callable.getProject(), callable.getContainingFile());

    final List<PyParameter> parameters = PyUtil.getParameters(callable, context);
    for (final PyParameter parameter : parameters) {
      parameter.getName();
    }


    if (callable instanceof PyFunction) {
      addKeywordArgumentVariantsForFunction(callExpr, ret, visited, (PyFunction)callable, parameters, context);
    }
    else {
      final Collection<String> parameterNames = new ArrayList<>();
      for (final PyParameter parameter : parameters) {
        final String name = parameter.getName();
        if (name != null) {
          parameterNames.add(name);
        }
      }
      addKeywordArgumentVariantsForCallable(callExpr, ret, parameterNames);
    }
  }

  private static void addKeywordArgumentVariantsForCallable(@NotNull final PyCallExpression callExpr,
                                                            @NotNull final List<LookupElement> ret,
                                                            @NotNull final Collection<String> parameterNames) {
    for (final String parameterName : parameterNames) {
      ret.add(PyUtil.createNamedParameterLookup(parameterName, callExpr.getProject()));
    }
  }

  private static void addKeywordArgumentVariantsForFunction(@NotNull final PyCallExpression callExpr,
                                                            @NotNull final List<LookupElement> ret,
                                                            @NotNull final Collection<PyCallable> visited,
                                                            @NotNull final PyFunction function,
                                                            @NotNull final List<PyParameter> parameters,
                                                            @NotNull final TypeEvalContext context) {
    boolean needSelf = function.getContainingClass() != null && function.getModifier() != PyFunction.Modifier.STATICMETHOD;
    final KwArgParameterCollector collector = new KwArgParameterCollector(needSelf, ret);


    for (PyParameter parameter : parameters) {
      parameter.accept(collector);
    }
    if (collector.hasKwArgs()) {
      for (PyKeywordArgumentProvider provider : Extensions.getExtensions(PyKeywordArgumentProvider.EP_NAME)) {
        final List<String> arguments = provider.getKeywordArguments(function, callExpr);
        for (String argument : arguments) {
          ret.add(PyUtil.createNamedParameterLookup(argument, callExpr.getProject()));
        }
      }
      KwArgFromStatementCallCollector fromStatementCallCollector = new KwArgFromStatementCallCollector(ret, collector.getKwArgs());
      final PyStatementList statementList = function.getStatementList();
      if (statementList != null) {
        statementList.acceptChildren(fromStatementCallCollector);
      }

      //if (collector.hasOnlySelfAndKwArgs()) {
      // nothing interesting besides self and **kwargs, let's look at superclass (PY-778)
      if (fromStatementCallCollector.isKwArgsTransit()) {

        final PsiElement superMethod = PySuperMethodsSearch.search(function, context).findFirst();
        if (superMethod instanceof PyFunction) {
          addKeywordArgumentVariants((PyFunction)superMethod, callExpr, ret, visited);
        }
      }
    }
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
          final LookupElement item = PyUtil.createNamedParameterLookup(namedParam.getName(), par.getProject());
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
        if (PyNames.isIdentifier(name)) {
          myRet.add(PyUtil.createNamedParameterLookup(name, argument.getProject()));
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
