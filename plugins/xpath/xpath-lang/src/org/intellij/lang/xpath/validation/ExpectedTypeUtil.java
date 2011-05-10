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

/*
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 04.05.2006
 * Time: 22:32:31
 */
package org.intellij.lang.xpath.validation;

import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.xpath.XPath2TokenTypes;
import org.intellij.lang.xpath.XPathElementType;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.context.XPathVersion;
import org.intellij.lang.xpath.context.functions.Function;
import org.intellij.lang.xpath.context.functions.Parameter;
import org.intellij.lang.xpath.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExpectedTypeUtil {
    private ExpectedTypeUtil() {
    }

    @NotNull
    public static XPathType getExpectedType(XPathExpression expression) {
        XPathType expectedType = XPathType.UNKNOWN;

        final XPathExpression parentExpr = PsiTreeUtil.getParentOfType(expression, XPathExpression.class);
        if (parentExpr != null) {
            if (parentExpr instanceof XPathBinaryExpression && expression == ((XPathBinaryExpression)parentExpr).getROperand()) {
              final XPathElementType op = ((XPathBinaryExpression)parentExpr).getOperator();
              final XPathExpression lop = ((XPathBinaryExpression)parentExpr).getLOperand();
                if (op == XPathTokenTypes.AND || op == XPathTokenTypes.OR) {
                    expectedType = XPathType.BOOLEAN;
                } else if (XPath2TokenTypes.NUMBER_OPERATIONS.contains(op)) {
                  if (isXPath1(expression)) {
                    expectedType = XPathType.NUMBER;
                  } else {
                    expectedType = matchingType(lop, op);
                  }
                } else if (XPath2TokenTypes.COMP_OPS.contains(op)) {
                  if (lop != null && lop.getType() != XPathType.NODESET) {
                    if ((expectedType = lop.getType()) == XPathType.BOOLEAN) {
                      if (!isXPath1(expression)) expectedType = XPath2Type.BOOLEAN_STRICT;
                    } else if (expectedType == XPath2Type.BOOLEAN) {
                      expectedType = XPath2Type.BOOLEAN_STRICT;
                    }
                  } else {
                    expectedType = XPathType.UNKNOWN;
                  }
                } else if (XPath2TokenTypes.INTERSECT_EXCEPT.contains(op)) {
                  expectedType = XPath2SequenceType.create(XPath2Type.NODE, XPath2SequenceType.Cardinality.ZERO_OR_MORE);
                } else if (op == XPath2TokenTypes.TO) {
                  expectedType = XPath2Type.INTEGER;
                } else {
                  expectedType = XPathType.UNKNOWN;
                }
            } else if (parentExpr instanceof XPathPrefixExpression) {
              expectedType = XPathType.NUMBER;
            } else if (parentExpr instanceof XPathFunctionCall) {
                final XPathFunctionCall call = (XPathFunctionCall)parentExpr;
                final XPathFunction xpathFunction = call.resolve();
                if (xpathFunction != null) {
                    final Function functionDecl = xpathFunction.getDeclaration();
                    if (functionDecl != null) {
                        final Parameter p = findParameterDecl(call.getArgumentList(), expression, functionDecl.getParameters());
                        if (p != null) {
                            expectedType = p.type;
                            if (expectedType == XPath2Type.BOOLEAN) {
                              expectedType = XPath2Type.BOOLEAN_STRICT;
                            }
                        }
                    }
                }
            } else if (parentExpr instanceof XPathStep) {
                final XPathStep step = (XPathStep)parentExpr;
                final XPathPredicate[] predicates = step.getPredicates();
                for (XPathPredicate predicate : predicates) {
                    if (predicate.getPredicateExpression() == expression) {
                        return mapType(expression, getPredicateType(expression));
                    }
                }

                if (isXPath1(expression)) {
                  expectedType = XPathType.NODESET;
                } else {
                  if (step.getStep() != null) {
                    expectedType = XPath2Type.SEQUENCE;
                  } else {
                    expectedType = XPath2Type.NODE;
                  }
                }
            } else if (parentExpr instanceof XPathLocationPath) {
                expectedType = isXPath1(expression) ? XPathType.NODESET : XPath2Type.SEQUENCE;
            } else if (parentExpr instanceof XPath2If) {
              if (expression == ((XPath2If)parentExpr).getCondition()) {
                return mapType(expression, XPath2Type.BOOLEAN);
              }
            } else if (parentExpr instanceof XPathFilterExpression) {
                final XPathFilterExpression filterExpression = (XPathFilterExpression)parentExpr;

              final XPathExpression filteredExpression = filterExpression.getExpression();
              if (filteredExpression == expression) {
                    return isXPath1(expression) ? XPathType.NODESET : XPath2Type.SEQUENCE;
                }

                assert ((XPathFilterExpression)parentExpr).getPredicate().getPredicateExpression() == expression;

                return mapType(expression, getPredicateType(expression));
            }
        } else {
            expectedType = expression.getXPathContext().getExpectedType(expression);
        }
        return mapType(expression, expectedType);
    }

  private static boolean isXPath1(XPathExpression expression) {
    return expression.getXPathVersion() == XPathVersion.V1;
  }

  private static XPathType matchingType(XPathExpression lOperand, XPathElementType op) {
    final XPathType type = mapType(lOperand, lOperand.getType());
    if (op == XPathTokenTypes.PLUS) {
      if (XPathType.isAssignable(XPath2Type.NUMERIC, type)) {
        return XPath2Type.NUMERIC;
      } else if (XPathType.isAssignable(XPath2Type.DATE, type) || XPathType.isAssignable(XPath2Type.TIME, type) || XPathType.isAssignable(XPath2Type.DATETIME, type)) {
        return XPath2Type.DURATION;
      } else if (XPathType.isAssignable(XPath2Type.DURATION, type)) {
        return XPathType.ChoiceType.create(XPath2Type.DURATION, XPath2Type.DATE, XPath2Type.TIME, XPath2Type.DATETIME);
      }
    } else if (op == XPathTokenTypes.MINUS) {
      if (XPathType.isAssignable(XPath2Type.NUMERIC, type)) {
        return XPath2Type.NUMERIC;
      } else if (XPathType.isAssignable(XPath2Type.DATE, type) || XPathType.isAssignable(XPath2Type.TIME, type) || XPathType.isAssignable(XPath2Type.DATETIME, type)) {
        return XPathType.ChoiceType.create((XPath2Type)type, XPath2Type.DURATION);
      } else if (XPathType.isAssignable(XPath2Type.DURATION, type)) {
        return XPath2Type.DURATION;
      }
    } else if (op == XPathTokenTypes.MULT) {
      if (XPathType.isAssignable(XPath2Type.NUMERIC, type)) {
        return XPathType.ChoiceType.create(XPath2Type.NUMERIC, XPath2Type.DURATION);
      } else if (XPath2Type.DURATION.isAssignableFrom(type)) {
        return XPath2Type.NUMERIC;
      }
    } else if (op == XPath2TokenTypes.IDIV || op == XPathTokenTypes.MOD) {
      return XPath2Type.NUMERIC;
    } else if (op == XPathTokenTypes.DIV) {
      if (XPathType.isAssignable(XPath2Type.NUMERIC, type)) {
        return XPath2Type.NUMERIC;
      } else if (XPath2Type.DURATION.isAssignableFrom(type)) {
        return XPath2Type.ChoiceType.create(XPath2Type.NUMERIC, XPath2Type.DURATION);
      }
    }

    // TODO
    return XPathType.UNKNOWN;
  }

    public static XPathType mapType(XPathExpression context, XPathType type) {
      return context.getXPathVersion() == XPathVersion.V2 ?
              XPath2Type.mapType(type) : type;
    }

    public static XPathType getPredicateType(XPathExpression expression) {
        // special: If the result is a number, the result will be converted to true if the number is equal to
        // the context position and will be converted to false otherwise;
        // (http://www.w3.org/TR/xpath#predicates)
        return expression.getType() == XPathType.NUMBER ? XPathType.NUMBER : XPathType.BOOLEAN;
    }

    @Nullable
    private static Parameter findParameterDecl(XPathExpression[] argumentList, XPathExpression expr, Parameter[] parameters) {
        for (int i = 0; i < argumentList.length; i++) {
            XPathExpression arg = argumentList[i];
            if (arg == expr) {
                if (i < parameters.length) {
                    return parameters[i];
                } else if (parameters.length > 0) {
                    final Parameter last = parameters[parameters.length - 1];
                    if (last.kind == Parameter.Kind.VARARG) {
                        return last;
                    }
                }
            }
        }
        return null;
    }

    public static boolean isExplicitConversion(XPathExpression expression) {
        expression = unparenthesize(expression);

        if (!(expression instanceof XPathFunctionCall)) {
            return false;
        }
        final XPathFunctionCall call = ((XPathFunctionCall)expression);
        if (call.getQName().getPrefix() != null || call.getArgumentList().length != 1) {
            return false;
        }
        return XPathType.fromString(call.getFunctionName()) != XPathType.UNKNOWN;
    }

    // TODO: put this somewhere else
    @Nullable
    public static XPathExpression unparenthesize(XPathExpression expression) {
        while (expression instanceof XPathParenthesizedExpression) {
            expression = ((XPathParenthesizedExpression)expression).getExpression();
        }
        return expression;
    }
}
