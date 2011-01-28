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
package org.intellij.lang.xpath.validation.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.Language;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.psi.*;
import org.intellij.lang.xpath.validation.ExpectedTypeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class IndexZeroPredicate extends XPathInspection {
    protected Visitor createVisitor(InspectionManager manager, boolean isOnTheFly) {
        return new MyVisitor(manager, isOnTheFly);
    }

    @NotNull
    public String getDisplayName() {
        return "Use of index 0 in XPath predicates";
    }

    @NotNull
    @NonNls
    public String getShortName() {
        return "IndexZeroUsage";
    }

    public boolean isEnabledByDefault() {
        return true;
    }

  protected boolean acceptsLanguage(Language language) {
    return language == XPathFileType.XPATH.getLanguage() || language == XPathFileType.XPATH2.getLanguage();
  }

  final static class MyVisitor extends Visitor {
        MyVisitor(InspectionManager manager, boolean isOnTheFly) {
            super(manager, isOnTheFly);
        }

        protected void checkPredicate(XPathPredicate predicate) {
            final XPathExpression expr = predicate.getPredicateExpression();
            if (expr != null) {
                if (expr.getType() == XPathType.NUMBER) {
                    if (isZero(expr)) {
                        addProblem(myManager.createProblemDescriptor(expr,
                                "Use of 0 as predicate index", (LocalQuickFix)null,
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly));
                    }
                } else if (expr instanceof XPathBinaryExpression && expr.getType() == XPathType.BOOLEAN) {
                    final XPathBinaryExpression expression = (XPathBinaryExpression)expr;
                    if (!XPathTokenTypes.BOOLEAN_OPERATIONS.contains(expression.getOperator())) {
                        return;
                    }
                    
                    final XPathExpression lOp = expression.getLOperand();
                    final XPathExpression rOp = expression.getROperand();

                    if (isZero(lOp)) {
                        assert lOp != null;

                        if (isPosition(rOp)) {
                            addProblem(myManager.createProblemDescriptor(expr,
                                    "Comparing position() to 0", (LocalQuickFix)null,
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly));
                        }
                    } else if (isZero(rOp)) {
                        assert rOp != null;

                        if (isPosition(lOp)) {
                            addProblem(myManager.createProblemDescriptor(expr,
                                    "Comparing position() to 0", (LocalQuickFix)null,
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly));
                        }
                    }
                }
            }
        }

        private static boolean isPosition(XPathExpression expression) {
            expression = ExpectedTypeUtil.unparenthesize(expression);

            if (!(expression instanceof XPathFunctionCall)) {
                return false;
            }

            final XPathFunctionCall call = (XPathFunctionCall)expression;
            final PrefixedName qName = call.getQName();
            if (qName.getPrefix() != null) return false;
            return "position".equals(qName.getLocalName());
        }

        private static boolean isZero(XPathExpression op) {
            op = ExpectedTypeUtil.unparenthesize(op);

            // TODO: compute constant expression
            return op != null && "0".equals(op.getText());
        }
    }
}
