/*
 * Copyright 2007 Sascha Weinreuter
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
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.Language;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.psi.XPathBinaryExpression;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathFunctionCall;
import org.intellij.lang.xpath.psi.XPathString;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class HardwiredNamespacePrefix extends XPathInspection {
    public boolean isEnabledByDefault() {
        return true;
    }

    protected Visitor createVisitor(final InspectionManager manager, final boolean isOnTheFly) {
        return new Visitor(manager, isOnTheFly) {
            protected void checkExpression(XPathExpression expression) {
                if (!(expression instanceof XPathBinaryExpression)) {
                    return;
                }
                final XPathBinaryExpression expr = (XPathBinaryExpression)expression;
                if (expr.getOperator() == XPathTokenTypes.EQ) {
                    final XPathExpression lop = expr.getLOperand();
                    final XPathExpression rop = expr.getROperand();

                    if (isNameComparison(lop, rop)) {
                        assert rop != null;
                        final ProblemDescriptor p = manager.createProblemDescriptor(rop, "Hardwired namespace prefix", isOnTheFly,
                                                                                    LocalQuickFix.EMPTY_ARRAY,
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                        addProblem(p);
                    } else if (isNameComparison(rop, lop)) {
                        assert lop != null;
                        final ProblemDescriptor p = manager.createProblemDescriptor(lop, "Hardwired namespace prefix", isOnTheFly,
                                                                                    LocalQuickFix.EMPTY_ARRAY,
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                        addProblem(p);
                    } else if (isNameFunctionCall(lop)) {
                        // TODO
                    } else if (isNameFunctionCall(rop)) {
                        // TODO
                    }
                }
            }
        };
    }

    private static boolean isNameComparison(XPathExpression op1, XPathExpression op2) {
        if (!isNameFunctionCall(op1)) return false;
        if (!(op2 instanceof XPathString)) {
            return false;
        }
        final String value = ((XPathString)op2).getValue();
        return value != null && value.contains(":");
    }

    private static boolean isNameFunctionCall(XPathExpression op1) {
        if (!(op1 instanceof XPathFunctionCall)) {
            return false;
        }
        final XPathFunctionCall fc = (XPathFunctionCall)op1;
        return "name".equals(fc.getFunctionName());
    }

    @Nls
    @NotNull
    public String getDisplayName() {
        return "Hardwired Namespace Prefix";
    }

    @NonNls
    @NotNull
    public String getShortName() {
        return "HardwiredNamespacePrefix";
    }

  protected boolean acceptsLanguage(Language language) {
    return language == XPathFileType.XPATH.getLanguage() || language == XPathFileType.XPATH2.getLanguage();
  }
}
