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
    final LanguageLevel languageLevel = ((PyFile)paramlist.getContainingFile()).getLanguageLevel();
    ParamHelper.walkDownParamArray(
      paramlist.getParameters(),
      new ParamHelper.ParamVisitor() {
        Set<String> parameterNames = new HashSet<String>();
        boolean hadPositionalContainer = false;
        boolean hadKeywordContainer = false;
        boolean hadDefaultValue = false;
        boolean hadSingleStar = false;
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
            if (hadPositionalContainer && !languageLevel.isPy3K()) {
              getHolder().createErrorAnnotation(parameter, PyBundle.message("ANN.regular.param.after.vararg"));
            }
            else if (hadKeywordContainer) {
              getHolder().createErrorAnnotation(parameter, PyBundle.message("ANN.regular.param.after.keyword"));
            }
            if (parameter.getDefaultValue() != null) {
              hadDefaultValue = true;
            }
            else {
              if (hadDefaultValue && !hadSingleStar && (!languageLevel.isPy3K() || !hadPositionalContainer)) {
                getHolder().createErrorAnnotation(parameter, PyBundle.message("ANN.non.default.param.after.default"));
              }
            }
          }
        }

        @Override
        public void visitSingleStarParameter(PySingleStarParameter param, boolean first, boolean last) {
          hadSingleStar = true;
        }
      }
    );
  }
}
