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

package com.jetbrains.python.validation;

import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.impl.ParamHelper;

import java.util.Set;

/**
 * Checks for anomalies in parameter lists of function declarations.
 */
public class ParameterListAnnotator extends PyAnnotator {
  @Override
  public void visitPyParameterList(final PyParameterList paramlist) {
    ParamHelper.walkDownParamArray(
      paramlist.getParameters(),
      new ParamHelper.ParamVisitor() {
        Set<String> parameterNames = new HashSet<String>();
        boolean hadPositionalContainer = false, hadKeywordContainer = false;
        boolean hadDefaultValue = false;
        @Override
        public void visitNamedParameter(PyNamedParameter parameter, boolean first, boolean last) {
          if (parameterNames.contains(parameter.getName())) {
            getHolder().createErrorAnnotation(parameter, PyBundle.message("ANN.duplicate.param.name"));
          }
          parameterNames.add(parameter.getName());
          if (parameter.isPositionalContainer()) {
            if (hadKeywordContainer) {
              getHolder().createErrorAnnotation(parameter, PyBundle.message("ANN.starred.param.after.kwparam"));
            }
            hadPositionalContainer = true;
          }
          else if (parameter.isKeywordContainer()) {
            hadKeywordContainer = true;
          }
          else {
            if (hadPositionalContainer || hadKeywordContainer) {
              getHolder().createErrorAnnotation(parameter, PyBundle.message("ANN.regular.param.after.starred"));
            }
            if (parameter.getDefaultValue() != null) {
              hadDefaultValue = true;
            }
            else {
              if (hadDefaultValue) {
                getHolder().createErrorAnnotation(parameter, PyBundle.message("ANN.non.default.param.after.default"));
              }
            }
          }
        }
      }
    );
  }
}
