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

import com.intellij.openapi.util.Comparing;
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

import javax.xml.namespace.QName;

public class ExpectedTypeUtil {
  private ExpectedTypeUtil() {
  }

  @NotNull
  public static XPathType getExpectedType(XPathExpression expression) {

    final XPathExpression parentExpr = PsiTreeUtil.getParentOfType(expression, XPathExpression.class);
    if (parentExpr != null) {
      final ExpectedTypeVisitor visitor = new ExpectedTypeVisitor(expression);
      parentExpr.accept(visitor);
      return mapType(expression, visitor.getExpectedType());
    } else {
      return mapType(expression, expression.getXPathContext().getExpectedType(expression));
    }
  }

  private static XPathType matchingType(XPathExpression lOperand, XPathElementType op) {
    final XPathType type = mapType(lOperand, lOperand.getType());
    if (op == XPathTokenTypes.PLUS) {
      if (XPathType.isAssignable(XPath2Type.NUMERIC, type)) {
        return XPath2Type.NUMERIC;
      } else if (XPathType.isAssignable(XPath2Type.DATE, type) ||
                 XPathType.isAssignable(XPath2Type.TIME, type) ||
                 XPathType.isAssignable(XPath2Type.DATETIME, type)) {
        return XPath2Type.DURATION;
      } else if (XPathType.isAssignable(XPath2Type.DURATION, type)) {
        return XPathType.ChoiceType.create(XPath2Type.DURATION, XPath2Type.DATE, XPath2Type.TIME, XPath2Type.DATETIME);
      }
    } else if (op == XPathTokenTypes.MINUS) {
      if (XPathType.isAssignable(XPath2Type.NUMERIC, type)) {
        return XPath2Type.NUMERIC;
      } else if (XPathType.isAssignable(XPath2Type.DATE, type) ||
                 XPathType.isAssignable(XPath2Type.TIME, type) ||
                 XPathType.isAssignable(XPath2Type.DATETIME, type)) {
        return XPathType.ChoiceType.create(type, XPath2Type.DURATION);
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
    return context.getXPathVersion() == XPathVersion.V2 ? XPath2Type.mapType(type) : type;
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
    if (call.getArgumentList().length != 1) {
      return false;
    } else if (call.getQName().getPrefix() != null) {
      XPathType type = call.getType();
      if (type instanceof XPath2SequenceType) {
        type = ((XPath2SequenceType)type).getType();
      }
      if (type instanceof XPath2Type) {
        final QName funcName = expression.getXPathContext().getQName(call);
        if (Comparing.equal(funcName, ((XPath2Type)type).getQName())) {
          return true;
        }
      }
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

  private static class ExpectedTypeVisitor extends XPath2ElementVisitor {
    private final XPathExpression myExpression;

    private XPathType myExpectedType = XPathType.UNKNOWN;

    public ExpectedTypeVisitor(XPathExpression expression) {
      myExpression = expression;
    }

    @Override
    public void visitXPathPrefixExpression(XPathPrefixExpression o) {
      myExpectedType = XPathType.NUMBER;
    }

    @Override
    public void visitXPathBinaryExpression(XPathBinaryExpression parent) {
      if (myExpression == parent.getROperand()) {
        final XPathElementType op = parent.getOperator();
        final XPathExpression lop = parent.getLOperand();
        if (op == XPathTokenTypes.AND || op == XPathTokenTypes.OR) {
          myExpectedType = XPathType.BOOLEAN;
        } else if (XPath2TokenTypes.NUMBER_OPERATIONS.contains(op)) {
          if (isXPath1(myExpression)) {
            myExpectedType = XPathType.NUMBER;
          } else {
            myExpectedType = matchingType(lop, op);
          }
        } else if (XPath2TokenTypes.COMP_OPS.contains(op)) {
          if (lop != null && lop.getType() != XPathType.NODESET) {
            if ((myExpectedType = lop.getType()) == XPathType.BOOLEAN) {
              if (!isXPath1(myExpression)) myExpectedType = XPath2Type.BOOLEAN_STRICT;
            } else if (myExpectedType == XPath2Type.BOOLEAN) {
              myExpectedType = XPath2Type.BOOLEAN_STRICT;
            }
          } else {
            myExpectedType = XPathType.UNKNOWN;
          }
        } else if (XPath2TokenTypes.INTERSECT_EXCEPT.contains(op)) {
          myExpectedType = XPath2SequenceType.create(XPath2Type.NODE, XPath2SequenceType.Cardinality.ZERO_OR_MORE);
        } else if (op == XPath2TokenTypes.TO) {
          myExpectedType = XPath2Type.INTEGER;
        } else {
          myExpectedType = XPathType.UNKNOWN;
        }
      } else {
        super.visitXPathBinaryExpression(parent);
      }
    }

    @Override
    public void visitXPathFunctionCall(XPathFunctionCall call) {
      final XPathFunction xpathFunction = call.resolve();
      if (xpathFunction != null) {
        final Function functionDecl = xpathFunction.getDeclaration();
        if (functionDecl != null) {
          final Parameter p = findParameterDecl(call.getArgumentList(), myExpression, functionDecl.getParameters());
          if (p != null) {
            if (p.type == XPath2Type.BOOLEAN) {
              myExpectedType = XPath2Type.BOOLEAN_STRICT;
            } else {
              myExpectedType = p.type;
            }
          }
        }
      }
    }

    @Override
    public void visitXPathFilterExpression(XPathFilterExpression filterExpression) {
      final XPathExpression filteredExpression = filterExpression.getExpression();
      if (filteredExpression == myExpression) {
        myExpectedType = isXPath1(myExpression) ? XPathType.NODESET : XPath2Type.SEQUENCE;
        return;
      }

      assert filterExpression.getPredicate().getPredicateExpression() == myExpression;
      myExpectedType = getPredicateType(myExpression);
    }

    @Override
    public void visitXPathStep(XPathStep step) {
      final XPathPredicate[] predicates = step.getPredicates();
      for (XPathPredicate predicate : predicates) {
        if (predicate.getPredicateExpression() == myExpression) {
          myExpectedType = getPredicateType(myExpression);
          return;
        }
      }

      if (isXPath1(step)) {
        myExpectedType = XPathType.NODESET;
      } else {
        if (step.getStep() != null) {
          myExpectedType = XPath2Type.SEQUENCE;
        } else {
          myExpectedType = XPath2Type.NODE;
        }
      }
    }

    @Override
    public void visitXPathLocationPath(XPathLocationPath expression) {
      myExpectedType = isXPath1(myExpression) ? XPathType.NODESET : XPath2Type.SEQUENCE;
    }

    @Override
    public void visitXPath2If(XPath2If expression) {
      if (myExpression == expression.getCondition()) {
        myExpectedType = XPath2Type.BOOLEAN;
      }
    }

    @Override
    public void visitXPath2QuantifiedExpr(XPath2QuantifiedExpr expression) {
      if (myExpression == expression.getTest()) {
        myExpectedType = XPath2Type.BOOLEAN;
      }
    }

    public XPathType getExpectedType() {
      return myExpectedType;
    }

    private static boolean isXPath1(XPathExpression expression) {
      return expression.getXPathVersion() == XPathVersion.V1;
    }
  }
}
