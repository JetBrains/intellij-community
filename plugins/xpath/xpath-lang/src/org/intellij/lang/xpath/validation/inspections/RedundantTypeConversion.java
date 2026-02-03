/*
 * Copyright 2006 Sascha Weinreuter
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
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.psi.XPath2SequenceType;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathFunctionCall;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.validation.ExpectedTypeUtil;
import org.intellij.lang.xpath.validation.inspections.quickfix.XPathQuickFixFactory;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RedundantTypeConversion extends XPathInspection {
    private static final @NonNls String SHORT_NAME = "RedundantTypeConversion";

    public boolean CHECK_ANY = false;

  @Override
  public @NotNull @NonNls String getShortName() {
        return SHORT_NAME;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected Visitor createVisitor(InspectionManager manager, boolean isOnTheFly) {
        return new MyElementVisitor(manager, isOnTheFly);
    }

  @Override
  protected boolean acceptsLanguage(Language language) {
      return language == XPathFileType.XPATH.getLanguage() || language == XPathFileType.XPATH2.getLanguage();
    }

    final class MyElementVisitor extends Visitor {

        MyElementVisitor(InspectionManager manager, boolean isOnTheFly) {
            super(manager, isOnTheFly);
        }

        @Override
        protected void checkExpression(final @NotNull XPathExpression expr) {
            if (ExpectedTypeUtil.isExplicitConversion(expr)) {
                final XPathExpression expression = ExpectedTypeUtil.unparenthesize(expr);
                assert expression != null;

                final XPathType convertedType = ((XPathFunctionCall)expression).getArgumentList()[0].getType();
                if (isSameType(expression, convertedType)) {
                    final XPathQuickFixFactory fixFactory = ContextProvider.getContextProvider(expression).getQuickFixFactory();
                    LocalQuickFix[] fixes = fixFactory.createRedundantTypeConversionFixes(expression);

                  final String message = XPathBundle.message("inspection.message.redundant.conversion.to.type", convertedType.getName());
                  addProblem(myManager.createProblemDescriptor(expression, message, myOnTheFly, fixes,
                                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
                } else if (CHECK_ANY) {
                    final XPathType expectedType = ExpectedTypeUtil.getExpectedType(expression);
                    if (expectedType == XPathType.ANY) {
                        final XPathQuickFixFactory fixFactory = ContextProvider.getContextProvider(expression).getQuickFixFactory();
                        LocalQuickFix[] fixes = fixFactory.createRedundantTypeConversionFixes(expression);

                      final String message = XPathBundle.message("inspection.message.redundant.conversion.to.type", expectedType.getName());
                      addProblem(myManager.createProblemDescriptor(expression, message, myOnTheFly, fixes,
                                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
                    }
                }
            }
        }

      private static boolean isSameType(XPathExpression expression, XPathType convertedType) {
        XPathType type = ExpectedTypeUtil.mapType(expression, expression.getType());
        while (type instanceof XPath2SequenceType) {
          type = ((XPath2SequenceType)type).getType();
        }
        while (convertedType instanceof XPath2SequenceType) {
          convertedType = ((XPath2SequenceType)convertedType).getType();
        }
        return ExpectedTypeUtil.mapType(expression, convertedType) == type;
      }
    }
}
