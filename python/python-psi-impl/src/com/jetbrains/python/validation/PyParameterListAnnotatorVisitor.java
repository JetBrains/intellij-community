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
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.jetbrains.python.PyNamesKt.isPrivate;

/**
 * Checks for anomalies in parameter lists of function declarations.
 */
public class PyParameterListAnnotatorVisitor extends PyElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  public PyParameterListAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyParameterList(final @NotNull PyParameterList paramlist) {
    final LanguageLevel languageLevel = LanguageLevel.forElement(paramlist);
    final var hasImplicit = paramlist.getParent() instanceof PyFunction function && PyFunctionImpl.isMethod(function);

    ParamHelper.walkDownParamArray(
      paramlist.getParameters(),
      new ParamHelper.ParamVisitor() {
        final Set<String> parameterNames = new HashSet<>();
        boolean hadPositionalContainer = false;
        boolean hadKeywordContainer = false;
        boolean hadDefaultValue = false;
        boolean hadKeyword = false;
        boolean hadSlash = false;
        boolean hadSingleStar = false;
        boolean hadParamsAfterSingleStar = false;
        int inTuple = 0;
        @Override
        public void visitNamedParameter(PyNamedParameter parameter, boolean first, boolean last) {
          final var name = parameter.getName();
          if (!hadKeyword && name != null && !(hasImplicit && first) && !isPrivate(name)) {
            hadKeyword = true;
          }
          else if (hadKeyword && !hadPositionalContainer && !hadSingleStar && name != null && !hadSlash && isPrivate(name)) {
            myHolder
              .newAnnotation(HighlightSeverity.WARNING, PyPsiBundle.message("ANN.positional.only.param.after.keyword"))
              .range(parameter)
              .create();
          }
          if (parameterNames.contains(parameter.getName())) {
            myHolder.markError(parameter, PyPsiBundle.message("ANN.duplicate.param.name"));
          }
          parameterNames.add(parameter.getName());
          if (parameter.isPositionalContainer()) {
            if (hadKeywordContainer) {
              myHolder.markError(parameter, PyPsiBundle.message("ANN.starred.param.after.kwparam"));
            }
            if (hadSingleStar) {
              myHolder.markError(parameter, PyPsiBundle.message("ANN.multiple.args"));
            }

            if (hadPositionalContainer) myHolder.markError(parameter, PyPsiBundle.message("ANN.multiple.args"));
            hadPositionalContainer = true;
          }
          else if (parameter.isKeywordContainer()) {
            if (hadKeywordContainer) myHolder.markError(parameter, PyPsiBundle.message("ANN.multiple.kwargs"));
            hadKeywordContainer = true;

            if (hadSingleStar && !hadParamsAfterSingleStar) {
              myHolder.markError(parameter, PyPsiBundle.message("ANN.named.parameters.after.star"));
            }
          }
          else {
            if (hadSingleStar) {
              hadParamsAfterSingleStar = true;
            }
            if (hadPositionalContainer && languageLevel.isPython2()) {
              myHolder.markError(parameter, PyPsiBundle.message("ANN.regular.param.after.vararg"));
            }
            else if (hadKeywordContainer) {
              myHolder.markError(parameter, PyPsiBundle.message("ANN.regular.param.after.keyword"));
            }
            if (parameter.hasDefaultValue()) {
              hadDefaultValue = true;
            }
            else {
              if (hadDefaultValue && !hadSingleStar && (languageLevel.isPython2() || !hadPositionalContainer) && inTuple == 0) {
                myHolder.markError(parameter, PyPsiBundle.message("ANN.non.default.param.after.default"));
              }
            }
          }
        }

        @Override
        public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          inTuple++;
          if (languageLevel.isPy3K()) {
            myHolder.markError(param, PyPsiBundle.message("ANN.tuple.py3"));
          }
          else if (!param.hasDefaultValue() && hadDefaultValue) {
            myHolder.markError(param, PyPsiBundle.message("ANN.non.default.param.after.default"));
          }
        }

        @Override
        public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          inTuple--;
        }

        @Override
        public void visitSlashParameter(@NotNull PySlashParameter param, boolean first, boolean last) {
          if (hadSlash) {
            myHolder.markError(param, PyPsiBundle.message("ANN.multiple.slash"));
          }
          hadSlash = true;
          if (hadPositionalContainer) {
            myHolder.markError(param, PyPsiBundle.message("ANN.slash.param.after.vararg"));
          }
          else if (hadKeywordContainer) {
            myHolder.markError(param, PyPsiBundle.message("ANN.slash.param.after.keyword"));
          }
          if (first) {
            myHolder.markError(param, PyPsiBundle.message("ANN.named.parameters.before.slash"));
          }
        }

        @Override
        public void visitSingleStarParameter(PySingleStarParameter param, boolean first, boolean last) {
          if (hadPositionalContainer || hadSingleStar) {
            myHolder.markError(param, PyPsiBundle.message("ANN.multiple.args"));
          }
          hadSingleStar = true;
          if (last) {
            myHolder.markError(param, PyPsiBundle.message("ANN.named.parameters.after.star"));
          }
        }
      }
    );
  }
}
