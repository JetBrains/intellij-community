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
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.ast.impl.ParamHelperCore;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameter-related things that should not belong directly to PyParameter.
 */
public final class ParamHelper {

  private ParamHelper() {
  }

  /**
   * Runs a {@link ParamWalker ParamWalker} down the array of parameters, recursively descending into tuple parameters.
   * If the array is from PyParamaterList.getParameters(), parameters are visited in the order of textual appearance
   * @param params where to walk
   * @param walker the walker with callbacks.
   */
  public static void walkDownParamArray(PyParameter[] params, ParamWalker walker) {
    walkDownParameters(ContainerUtil.map(params, PyCallableParameterImpl::psi), walker);
  }

  public static void walkDownParameters(@NotNull List<? extends PyCallableParameter> parameters, @NotNull ParamWalker walker) {
    int i = 0;
    for (PyCallableParameter parameter : parameters) {
      final PyParameter psi = parameter.getParameter();
      final boolean first = i == 0;
      final boolean last = i == parameters.size() - 1;

      if (psi instanceof PyTupleParameter tupleParameter) {
        walker.enterTupleParameter(tupleParameter, first, last);
        walkDownParamArray(tupleParameter.getContents(), walker);
        walker.leaveTupleParameter(tupleParameter, first, last);
      }
      else if (psi instanceof PyNamedParameter) {
        walker.visitNamedParameter((PyNamedParameter)psi, first, last);
      }
      else if (psi instanceof PySlashParameter) {
        walker.visitSlashParameter((PySlashParameter)psi, first, last);
      }
      else if (psi instanceof PySingleStarParameter) {
        walker.visitSingleStarParameter((PySingleStarParameter)psi, first, last);
      }
      else {
        walker.visitNonPsiParameter(parameter, first, last);
      }
      i++;
    }
  }

  @NotNull
  public static String getPresentableText(PyParameter @NotNull [] parameters,
                                          boolean includeDefaultValue,
                                          @Nullable TypeEvalContext context) {
    return getPresentableText(ContainerUtil.map(parameters, PyCallableParameterImpl::psi), includeDefaultValue, context);
  }

  @NotNull
  public static String getPresentableText(@NotNull List<? extends PyCallableParameter> parameters,
                                          boolean includeDefaultValue,
                                          @Nullable TypeEvalContext context) {
    final StringBuilder result = new StringBuilder();
    result.append("(");

    walkDownParameters(
      parameters,
      new ParamHelper.ParamWalker() {
        @Override
        public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          result.append("(");
        }

        @Override
        public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          result.append(")");
          if (!last) result.append(", ");
        }

        @Override
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          visitNonPsiParameter(PyCallableParameterImpl.psi(param), first, last);
        }

        @Override
        public void visitSlashParameter(@NotNull PySlashParameter param, boolean first, boolean last) {
          result.append(PySlashParameter.TEXT);
          if (!last) result.append(", ");
        }

        @Override
        public void visitSingleStarParameter(PySingleStarParameter param, boolean first, boolean last) {
          result.append(PySingleStarParameter.TEXT);
          if (!last) result.append(", ");
        }

        @Override
        public void visitNonPsiParameter(@NotNull PyCallableParameter parameter, boolean first, boolean last) {
          result.append(parameter.getPresentableText(includeDefaultValue, context));
          if (!last) result.append(", ");
        }
      }
    );

    result.append(")");
    return result.toString();
  }

  @NotNull
  public static String getNameInSignature(@NotNull PyCallableParameter parameter) {
    final StringBuilder sb = new StringBuilder();

    if (parameter.isPositionalContainer()) sb.append("*");
    else if (parameter.isKeywordContainer()) sb.append("**");

    final String name = parameter.getName();
    sb.append(name != null ? name : "...");

    return sb.toString();
  }

  @NotNull
  public static String getNameInSignature(@NotNull PyNamedParameter parameter) {
    return ParamHelperCore.getNameInSignature(parameter);
  }

  /**
   * @param defaultValue             string returned by {@link PyCallableParameter#getDefaultValueText()} or {@link PyParameter#getDefaultValueText()}.
   * @param parameterRenderedAsTyped true if parameter is rendered with type annotation.
   * @return equal sign (surrounded with spaces if {@code parameterRenderedAsTyped}) +
   * {@code defaultValue} (with body escaped if it is a string literal)
   */
  @Nullable
  @Contract("null, _->null")
  public static String getDefaultValuePartInSignature(@Nullable String defaultValue, boolean parameterRenderedAsTyped) {
    return ParamHelperCore.getDefaultValuePartInSignature(defaultValue, parameterRenderedAsTyped);
  }

  public static boolean couldHaveDefaultValue(@NotNull String parameterName) {
    return !parameterName.startsWith("*") && !parameterName.equals(PySlashParameter.TEXT);
  }

  public static boolean isSelfArgsKwargsCallable(@Nullable PsiElement element, @NotNull TypeEvalContext context) {
    if (element instanceof PyCallable) {
      final List<PyCallableParameter> parameters = ((PyCallable)element).getParameters(context);
      return parameters.size() == 3 &&
             parameters.get(0).isSelf() &&
             parameters.get(1).isPositionalContainer() &&
             parameters.get(2).isKeywordContainer();
    }

    return false;
  }

  public interface ParamWalker {
    /**
     * Is called when a tuple parameter is encountered, before visiting any parameters nested in it.
     * @param param the parameter
     * @param first true iff it is the first in the list
     * @param last true it is the last in the list
     */
    void enterTupleParameter(PyTupleParameter param, boolean first, boolean last);

    /**
     * Is called when all nested parameters of a given tuple parameter are visited.
     * @param param the parameter
     * @param first true iff it is the first in the list
     * @param last true it is the last in the list
     */
    void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last);

    /**
     * Is called when a named parameter is encountered.
     * @param param the parameter
     * @param first true iff it is the first in the list
     * @param last true it is the last in the list
     */
    void visitNamedParameter(PyNamedParameter param, boolean first, boolean last);

    void visitSlashParameter(@NotNull PySlashParameter param, boolean first, boolean last);

    void visitSingleStarParameter(PySingleStarParameter param, boolean first, boolean last);

    void visitNonPsiParameter(@NotNull PyCallableParameter parameter, boolean first, boolean last);
  }

  public static abstract class ParamVisitor implements ParamWalker {

    @Override
    public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) { }

    @Override
    public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) { }

    @Override
    public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) { }

    @Override
    public void visitSlashParameter(@NotNull PySlashParameter param, boolean first, boolean last) { }

    @Override
    public void visitSingleStarParameter(PySingleStarParameter param, boolean first, boolean last) { }

    @Override
    public void visitNonPsiParameter(@NotNull PyCallableParameter parameter, boolean first, boolean last) { }
  }

  public static List<PyNamedParameter> collectNamedParameters(PyParameterList plist) {
    final List<PyNamedParameter> result = new ArrayList<>(10); // a random 'enough'
    walkDownParamArray(
      plist.getParameters(),
      new ParamVisitor() {
        @Override
        public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
          result.add(param);
        }
      }
    );
    return result;
  }

}
