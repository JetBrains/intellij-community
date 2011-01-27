/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.validation;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.intellij.lang.xpath.XPathFile;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.psi.*;
import org.intellij.lang.xpath.xslt.context.Xslt2ContextProvider;

// TODO: more detailed error descriptions

@SuppressWarnings({"SimplifiableIfStatement"})
class XsltPatternValidator {
  private XsltPatternValidator() {
  }

  public static void validate(AnnotationHolder annotationHolder, PsiFile file) {
    final XPathExpression expression = ((XPathFile)file).getExpression();
    if (expression != null) {
      if (!checkPattern(expression)) {
        annotationHolder.createErrorAnnotation(expression, "Bad pattern");
      }
    } else {
      annotationHolder.createErrorAnnotation(TextRange.from(0, 1), "Missing pattern");
    }
  }

  private static boolean checkPattern(XPathExpression element) {
    if (element instanceof XPathBinaryExpression) {
      final XPathBinaryExpression expression = (XPathBinaryExpression)element;
      if (expression.getOperator() == XPathTokenTypes.UNION) {
        if (checkPattern(expression.getLOperand()) && checkPattern(expression.getROperand())) {
          return true;
        }
      }
    } else if (element instanceof XPathLocationPath) {
      return checkLocationPathPattern((XPathLocationPath)element);
    } else if (element instanceof XPathFunctionCall) {
      return checkIdKeyPattern(element);
    }
    return false;
  }

  private static boolean checkLocationPathPattern(XPathLocationPath locationPath) {
    final XPathStep step = locationPath.getFirstStep();
    // missing step is already flagged by parser
    return step == null || checkStep(step);
  }

  private static boolean checkStep(XPathStep step) {
    XPathExpression prev = step.getPreviousStep();
    if (prev instanceof XPathFilterExpression) {
      prev = ((XPathFilterExpression)prev).getExpression();
      if (prev != null && !(prev instanceof XPathStep)) {
        // stuff like key('', '')[1]/xxx is invalid: "The filtered expression in a pattern must be a simple step"
        return false;
      }
    }

    if (prev instanceof XPathStep) {
      if (!checkStep((XPathStep)prev)) {
        return false;
      }
    } else if (prev instanceof XPathFunctionCall) {
      if (!checkIdKeyPattern(prev)) {
        return false;
      }
    } else if (step.getStep() != null || prev != null) {
      return false;
    }

    final XPathAxisSpecifier axisSpecifier = step.getAxisSpecifier();
    if (axisSpecifier != null) {
      if ((axisSpecifier.getAxis() == Axis.CHILD || axisSpecifier.getAxis() == Axis.ATTRIBUTE)) {
        return true;
      }
    } else {
      return true;
    }
    return false;
  }

  private static boolean checkIdKeyPattern(PsiElement child) {
    if (child instanceof XPathFunctionCall) {
      final XPathFunctionCall call = (XPathFunctionCall)child;
      final XPathExpression[] arguments = call.getArgumentList();
      if ("id".equals(call.getFunctionName())) {
        if (arguments.length != 1) return false;
        return isIdValue(arguments[0]) ;
      } else if ("key".equals(call.getFunctionName())) {
        if (arguments.length != 2) return false;
        return arguments[0] instanceof XPathString && isKeyValue(arguments[1]);
      }
    }
    return false;
  }

  private static boolean isIdValue(XPathExpression argument) {
    if (argument.getXPathContext() instanceof Xslt2ContextProvider) {
      return argument instanceof XPathString || argument instanceof XPathVariableReference;
    } else {
      return argument instanceof XPathString;
    }
  }

  private static boolean isKeyValue(XPathExpression argument) {
    if (argument.getXPathContext() instanceof Xslt2ContextProvider) {
      return argument instanceof XPathString || argument instanceof XPathNumber || argument instanceof XPathVariableReference;
    } else {
      return argument instanceof XPathString;
    }
  }
}