// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.references;

import com.intellij.codeInsight.lookup.LookupElement;
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

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

public class KeywordArgumentCompletionUtil {
  public static void collectFunctionArgNames(PyElement element,
                                             List<? super LookupElement> ret,
                                             @NotNull final TypeEvalContext context,
                                             final boolean addEquals) {
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
        Set<String> namedArgsAlready = StreamEx.of(callExpr.getArgumentList().getArguments())
          .select(PyKeywordArgument.class)
          .map(PyKeywordArgument::getKeyword)
          .toSet();
        final List<LookupElement> extra = PyTypeUtil.toStream(calleeType)
          .select(PyCallableType.class)
          .flatMap(type -> collectParameterNamesFromType(type, callExpr, context).stream())
          .filter(it -> !namedArgsAlready.contains(it))
          .map(name -> PyUtil.createNamedParameterLookup(name, element.getContainingFile(), addEquals))
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
        int indexOfPySlashParameter = getIndexOfPySlashParameter(parameters);
        for (PyCallableParameter parameter : parameters.subList(indexOfPySlashParameter + 1, parameters.size())) {
          if (parameter.isKeywordContainer() || parameter.isPositionalContainer()) {
            continue;
          }
          ContainerUtil.addIfNotNull(result, parameter.getName());
        }
        PyFunction func = null;
        if (type.getCallable() instanceof PyFunction) {
          func = as(type.getCallable(), PyFunction.class);
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
    final PyResolveContext resolveContext = PyResolveContext.implicitContext().withTypeEvalContext(context);
    final QualifiedResolveResult result = callee.followAssignmentsChain(resolveContext);
    return result.getElement();
  }

  private static int getIndexOfPySlashParameter(@NotNull List<PyCallableParameter> parameters) {
    return ContainerUtil.indexOf(parameters, parameter -> parameter.getParameter() instanceof PySlashParameter);
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

    List<PyCallableParameter> parameters = function.getParameters(context);
    int indexOfPySlashParameter = getIndexOfPySlashParameter(parameters);

    StreamEx
      .of(parameters)
      .skip(indexOfPySlashParameter + 1)
      .map(PyCallableParameter::getParameter)
      .nonNull()
      .forEach(parameter -> parameter.accept(collector));

    if (collector.hasKwArgs()) {
      for (PyKeywordArgumentProvider provider : PyKeywordArgumentProvider.EP_NAME.getExtensionList()) {
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
    private final List<? super String> myRet;
    private boolean myHasSelf = false;
    private boolean myHasKwArgs = false;
    private PyParameter kwArgsParam = null;

    public KwArgParameterCollector(boolean needSelf, List<? super String> ret) {
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
            if (Objects.equals(myKwArgs.getName(), kw.getFirstChild().getNextSibling().getText())) {
              kwArgsTransit = true;
              break;
            }
          }
        }
      }
      super.visitPyCallExpression(node);
    }

    private void processGet(String operandName, PyExpression argument) {
      if (Objects.equals(myKwArgs.getName(), operandName) &&
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
