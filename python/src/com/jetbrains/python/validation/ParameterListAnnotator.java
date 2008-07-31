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
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;

/**
 * Checks for anomalies in parameter lists of function declarations.
 * User: yole
 * Date: 12.06.2005
 */
public class ParameterListAnnotator extends PyAnnotator {
    @Override public void visitPyParameterList(final PyParameterList node) {
        HashSet<String> parameterNames = new HashSet<String>();
        PyParameter[] parameters = node.getParameters();
        boolean hadPositionalContainer = false, hadKeywordContainer = false;
        boolean hadDefaultValue = false;
        for (PyParameter parameter: parameters) {
            if (parameterNames.contains(parameter.getName())) {
                getHolder().createErrorAnnotation(parameter, "duplicate parameter name");
            }
            parameterNames.add(parameter.getName());
            if (parameter.isPositionalContainer()) {
                if (hadKeywordContainer) {
                    getHolder().createErrorAnnotation(parameter, "* parameter after ** paremeter");
                }
                hadPositionalContainer = true;
            }
            else if (parameter.isKeywordContainer()) {
                hadKeywordContainer = true;
            }
            else {
                if (hadPositionalContainer || hadKeywordContainer) {
                    getHolder().createErrorAnnotation(parameter, "regular parameter after * or ** parameter");
                }
                if (parameter.getDefaultValue() != null) {
                    hadDefaultValue = true;
                }
                else {
                    if (hadDefaultValue) {
                        getHolder().createErrorAnnotation(parameter, "non-default parameter follows default parameter");
                    }
                }
            }
        }
    }
}
