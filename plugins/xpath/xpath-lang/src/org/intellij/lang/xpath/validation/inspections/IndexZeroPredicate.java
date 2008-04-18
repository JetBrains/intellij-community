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

import org.intellij.lang.xpath.psi.*;
import org.intellij.lang.xpath.validation.ExpectedTypeUtil;
import org.intellij.lang.xpath.XPathTokenTypes;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class IndexZeroPredicate extends XPathInspection {
    protected Visitor createVisitor(InspectionManager manager) {
        return new MyVisitor(manager);
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

    final static class MyVisitor extends Visitor {
        MyVisitor(InspectionManager manager) {
            super(manager);
        }

        protected void checkPredicate(XPathPredicate predicate) {
            final XPathExpression expr = predicate.getPredicateExpression();
            if (expr != null) {
                if (expr.getType() == XPathType.NUMBER) {
                    if (isZero(expr)) {
                        addProblem(myManager.createProblemDescriptor(expr,
                                "Use of 0 as predicate index", (LocalQuickFix)null,
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
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
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
                        }
                    } else if (isZero(rOp)) {
                        assert rOp != null;

                        if (isPosition(lOp)) {
                            addProblem(myManager.createProblemDescriptor(expr,
                                    "Comparing position() to 0", (LocalQuickFix)null,
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
                        }
                    }
                }
            }
        }

        private boolean isPosition(XPathExpression expression) {
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
