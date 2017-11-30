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

import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;

import java.util.Set;

/**
 * Checks for anomalies in parameter lists of function declarations.
 */
public class ParameterListAnnotator extends PyAnnotator {
  @Override
  public void visitPyParameterList(final PyParameterList paramlist) {
    final LanguageLevel languageLevel = LanguageLevel.forElement(paramlist);
    ParamHelper.walkDownParamArray(
      paramlist.getParameters(),
      new ParamHelper.ParamVisitor() {
        final Set<String> parameterNames = new HashSet<>();
        boolean hadPositionalContainer = false;
        boolean hadKeywordContainer = false;
        boolean hadDefaultValue = false;
        boolean hadSingleStar = false;
        boolean hadParamsAfterSingleStar = false;
        int inTuple = 0;
        @Override
        public void visitNamedParameter(PyNamedParameter parameter, boolean first, boolean last) {
          if (parameterNames.contains(parameter.getName())) {
            markError(parameter, PyBundle.message("ANN.duplicate.param.name"));
          }
          parameterNames.add(parameter.getName());
          if (parameter.isPositionalContainer()) {
            if (hadKeywordContainer) {
              markError(parameter, PyBundle.message("ANN.starred.param.after.kwparam"));
            }
            if (hadSingleStar) {
              markError(parameter, PyBundle.message("ANN.multiple.args"));
            }

            if (hadPositionalContainer) markError(parameter, PyBundle.message("ANN.multiple.args"));
            hadPositionalContainer = true;
          }
          else if (parameter.isKeywordContainer()) {
            if (hadKeywordContainer) markError(parameter, PyBundle.message("ANN.multiple.kwargs"));
            hadKeywordContainer = true;

            if (hadSingleStar && !hadParamsAfterSingleStar) {
              markError(parameter, PyBundle.message("ANN.named.parameters.after.star"));
            }
          }
          else {
            if (hadSingleStar) {
              hadParamsAfterSingleStar = true;
            }
            if (hadPositionalContainer && languageLevel.isPython2()) {
              markError(parameter, PyBundle.message("ANN.regular.param.after.vararg"));
            }
            else if (hadKeywordContainer) {
              markError(parameter, PyBundle.message("ANN.regular.param.after.keyword"));
            }
            if (parameter.hasDefaultValue()) {
              hadDefaultValue = true;
            }
            else {
              if (hadDefaultValue && !hadSingleStar && (languageLevel.isPython2() || !hadPositionalContainer) && inTuple == 0) {
                markError(parameter, PyBundle.message("ANN.non.default.param.after.default"));
              }
            }
          }
        }

        @Override
        public void enterTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          inTuple++;
          if (languageLevel.isPy3K()) {
            markError(param, PyBundle.message("ANN.tuple.py3"));
          }
          else if (!param.hasDefaultValue() && hadDefaultValue) {
            markError(param, PyBundle.message("ANN.non.default.param.after.default"));
          }
        }

        @Override
        public void leaveTupleParameter(PyTupleParameter param, boolean first, boolean last) {
          inTuple--;
        }

        @Override
        public void visitSingleStarParameter(PySingleStarParameter param, boolean first, boolean last) {
          if (hadPositionalContainer || hadSingleStar) {
            markError(param, PyBundle.message("ANN.multiple.args"));
          }
          hadSingleStar = true;
          if (last) {
            markError(param, PyBundle.message("ANN.named.parameters.after.star"));
          }
        }
      }
    );
  }
}
