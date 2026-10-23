// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.jetbrains.python.ProtectionLevel;
import com.jetbrains.python.PySyntaxCoreBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.ast.PyAstElementVisitor;
import com.jetbrains.python.ast.PyAstNamedParameter;
import com.jetbrains.python.ast.PyAstParameterList;
import com.jetbrains.python.ast.PyAstSingleStarParameter;
import com.jetbrains.python.ast.PyAstSlashParameter;
import com.jetbrains.python.ast.PyAstTupleParameter;
import com.jetbrains.python.ast.impl.ParamHelperCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Checks for anomalies in parameter lists of function declarations.
 */
@ApiStatus.Internal
public class PyParameterListAnnotatorVisitor extends PyAstElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  public PyParameterListAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyParameterList(final @NotNull PyAstParameterList paramlist) {
    final LanguageLevel languageLevel = LanguageLevel.forElement(paramlist);

    ParamHelperCore.walkDownParamArray(
      paramlist.getParameters(),
      new ParamHelperCore.ParamVisitor() {
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
        public void visitNamedParameter(PyAstNamedParameter parameter, boolean first, boolean last) {
          final var name = parameter.getName();
          if (!hadKeyword && name != null && !parameter.isSelf() && parameter.getProtectionLevel() != ProtectionLevel.PRIVATE) {
            hadKeyword = true;
          }
          else if (hadKeyword && !hadPositionalContainer && !hadSingleStar && name != null && !hadSlash && parameter.getProtectionLevel() == ProtectionLevel.PRIVATE) {
            myHolder
              .newAnnotation(HighlightSeverity.WARNING, PySyntaxCoreBundle.message("ANN.positional.only.param.after.keyword"))
              .range(parameter)
              .create();
          }
          if (parameterNames.contains(parameter.getName())) {
            myHolder.markError(parameter, PySyntaxCoreBundle.message("ANN.duplicate.param.name"));
          }
          parameterNames.add(parameter.getName());
          if (parameter.isPositionalContainer()) {
            if (hadKeywordContainer) {
              myHolder.markError(parameter, PySyntaxCoreBundle.message("ANN.starred.param.after.kwparam"));
            }
            if (hadSingleStar) {
              myHolder.markError(parameter, PySyntaxCoreBundle.message("ANN.multiple.args"));
            }

            if (hadPositionalContainer) myHolder.markError(parameter, PySyntaxCoreBundle.message("ANN.multiple.args"));
            hadPositionalContainer = true;
          }
          else if (parameter.isKeywordContainer()) {
            if (hadKeywordContainer) myHolder.markError(parameter, PySyntaxCoreBundle.message("ANN.multiple.kwargs"));
            hadKeywordContainer = true;

            if (hadSingleStar && !hadParamsAfterSingleStar) {
              myHolder.markError(parameter, PySyntaxCoreBundle.message("ANN.named.parameters.after.star"));
            }
          }
          else {
            if (hadSingleStar) {
              hadParamsAfterSingleStar = true;
            }
            if (hadPositionalContainer && languageLevel.isPython2()) {
              myHolder.markError(parameter, PySyntaxCoreBundle.message("ANN.regular.param.after.vararg"));
            }
            else if (hadKeywordContainer) {
              myHolder.markError(parameter, PySyntaxCoreBundle.message("ANN.regular.param.after.keyword"));
            }
            if (parameter.hasDefaultValue()) {
              hadDefaultValue = true;
            }
            else {
              if (hadDefaultValue && !hadSingleStar && (languageLevel.isPython2() || !hadPositionalContainer) && inTuple == 0) {
                myHolder.markError(parameter, PySyntaxCoreBundle.message("ANN.non.default.param.after.default"));
              }
            }
          }
        }

        @Override
        public void enterTupleParameter(PyAstTupleParameter param, boolean first, boolean last) {
          inTuple++;
          if (languageLevel.isPy3K()) {
            myHolder.markError(param, PySyntaxCoreBundle.message("ANN.tuple.py3"));
          }
          else if (!param.hasDefaultValue() && hadDefaultValue) {
            myHolder.markError(param, PySyntaxCoreBundle.message("ANN.non.default.param.after.default"));
          }
        }

        @Override
        public void leaveTupleParameter(PyAstTupleParameter param, boolean first, boolean last) {
          inTuple--;
        }

        @Override
        public void visitSlashParameter(PyAstSlashParameter param, boolean first, boolean last) {
          if (hadSlash) {
            myHolder.markError(param, PySyntaxCoreBundle.message("ANN.multiple.slash"));
          }
          hadSlash = true;
          if (hadPositionalContainer) {
            myHolder.markError(param, PySyntaxCoreBundle.message("ANN.slash.param.after.vararg"));
          }
          else if (hadKeywordContainer) {
            myHolder.markError(param, PySyntaxCoreBundle.message("ANN.slash.param.after.keyword"));
          }
          if (first) {
            myHolder.markError(param, PySyntaxCoreBundle.message("ANN.named.parameters.before.slash"));
          }
        }

        @Override
        public void visitSingleStarParameter(PyAstSingleStarParameter param, boolean first, boolean last) {
          if (hadPositionalContainer || hadSingleStar) {
            myHolder.markError(param, PySyntaxCoreBundle.message("ANN.multiple.args"));
          }
          hadSingleStar = true;
          if (last) {
            myHolder.markError(param, PySyntaxCoreBundle.message("ANN.named.parameters.after.star"));
          }
        }
      }
    );
  }
}
