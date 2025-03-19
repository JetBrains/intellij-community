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
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.lang.Language;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathFunctionCall;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.validation.ExpectedTypeUtil;
import org.intellij.lang.xpath.validation.inspections.quickfix.XPathQuickFixFactory;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;

import static com.intellij.codeInspection.options.OptPane.*;

// TODO: Option to flag literals: <number> = '123', <string> = 123, etc.
public class ImplicitTypeConversion extends XPathInspection {
    private static final @NonNls String SHORT_NAME = "ImplicitTypeConversion";
  private static final String STRING = "STRING";
  private static final String NODESET = "NODESET";
  private static final String NUMBER = "NUMBER";
  private static final String BOOLEAN = "BOOLEAN";

  public long BITS = 1720;
    private final BitSet OPTIONS = new BitSet(12);

    public boolean FLAG_EXPLICIT_CONVERSION = true;
    public boolean IGNORE_NODESET_TO_BOOLEAN_VIA_STRING = true;

    public ImplicitTypeConversion() {
        update();
    }

    private void update() {
        for (int i=0; i<12; i++) {
            final boolean b = (BITS & (1 << i)) != 0;
            OPTIONS.set(i, b);
        }
    }

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
  public @NotNull OptPane getOptionsPane() {
    //noinspection InjectedReferences
    return pane(
      group(XPathBundle.message("label.to", STRING),
            checkbox("0", XPathBundle.message("label.from", NODESET)),
            checkbox("2", XPathBundle.message("label.from", NUMBER)),
            checkbox("3", XPathBundle.message("label.from", BOOLEAN))).prefix("c"),
      group(XPathBundle.message("label.to", NUMBER),
            checkbox("4", XPathBundle.message("label.from", NODESET)),
            checkbox("5", XPathBundle.message("label.from", STRING)),
            checkbox("7", XPathBundle.message("label.from", BOOLEAN))).prefix("c"),
      group(XPathBundle.message("label.to", BOOLEAN),
            checkbox("8", XPathBundle.message("label.from", NODESET)),
            checkbox("9", XPathBundle.message("label.from", STRING)),
            checkbox("10", XPathBundle.message("label.from", NUMBER))).prefix("c"),
      separator(),
      checkbox("FLAG_EXPLICIT_CONVERSION", XPathBundle.message("checkbox.always.flag.explicit.conversion.to.unexpected.type")),
      checkbox("IGNORE_NODESET_TO_BOOLEAN_VIA_STRING", XPathBundle.message("checkbox.ignore.conversion.of.nodeset.to.boolean.by.string.conversion"))
    );
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return super.getOptionController().onPrefix(
      "c",
      bindId -> OPTIONS.get(Integer.parseInt(bindId)),
      (bindId, value) -> OPTIONS.set(Integer.parseInt(bindId), (Boolean) value));
  }

  @Override
    public void readSettings(@NotNull Element node) throws InvalidDataException {
        super.readSettings(node);
        update();
    }

    @Override
    public void writeSettings(@NotNull Element node) throws WriteExternalException {
        BITS = 0;
        for (int i=11; i>=0; i--) {
            BITS <<= 1;
            if (OPTIONS.get(i)) BITS |= 1;
        }
        super.writeSettings(node);
    }

    @Override
    protected boolean acceptsLanguage(Language language) {
      return language == XPathFileType.XPATH.getLanguage();
    }

    final class MyElementVisitor extends Visitor {
        MyElementVisitor(InspectionManager manager, boolean isOnTheFly) {
            super(manager, isOnTheFly);
        }

        @Override
        protected void checkExpression(@NotNull XPathExpression expression) {
            final XPathType expectedType = ExpectedTypeUtil.getExpectedType(expression);
            // conversion to NODESET is impossible (at least not in a portable way) and is flagged by annotator
            if (expectedType != XPathType.NODESET && expectedType != XPathType.UNKNOWN) {
                final boolean isExplicit = FLAG_EXPLICIT_CONVERSION &&
                        ExpectedTypeUtil.isExplicitConversion(expression);
                checkExpressionOfType(expression, expectedType, isExplicit);
            }
        }

        private void checkExpressionOfType(@NotNull XPathExpression expression, XPathType type, boolean explicit) {
            final XPathType exprType = expression.getType();
            if (exprType.isAbstract() || type.isAbstract()) return;

            if (exprType != type && (explicit || isCheckedConversion(exprType, type))) {
                if (explicit && exprType == XPathType.STRING && type == XPathType.BOOLEAN) {
                    final XPathExpression expr = ExpectedTypeUtil.unparenthesize(expression);
                    if (expr instanceof XPathFunctionCall && IGNORE_NODESET_TO_BOOLEAN_VIA_STRING &&
                            ((XPathFunctionCall)expr).getArgumentList()[0].getType() == XPathType.NODESET)
                    {
                        return;
                    }
                }

                final LocalQuickFix[] fixes;
                if (type != XPathType.NODESET) {
                    final XPathQuickFixFactory fixFactory = ContextProvider.getContextProvider(expression).getQuickFixFactory();
                    explicit = explicit && !(exprType == XPathType.STRING && type == XPathType.BOOLEAN);
                    fixes = fixFactory.createImplicitTypeConversionFixes(expression, type, explicit);
                } else {
                    fixes = null;
                }

                addProblem(
                  myManager.createProblemDescriptor(expression,
                                                    XPathBundle.message("inspection.message.expression.should.be.type", type.getName()),
                                                    myOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            }
        }

        private boolean isCheckedConversion(XPathType exprType, XPathType type) {

            if (exprType == XPathType.NODESET) {
                if (type == XPathType.STRING && OPTIONS.get(0)) return true;
                if (type == XPathType.NUMBER && OPTIONS.get(4)) return true;
                if (type == XPathType.BOOLEAN && OPTIONS.get(8)) return true;
            } else if (exprType == XPathType.STRING) {
                if (type == XPathType.NUMBER && OPTIONS.get(5)) return true;
                if (type == XPathType.BOOLEAN && OPTIONS.get(9)) return true;
            } else if (exprType == XPathType.NUMBER) {
                if (type == XPathType.STRING && OPTIONS.get(2)) return true;
                if (type == XPathType.BOOLEAN && OPTIONS.get(10)) return true;
            } else if (exprType == XPathType.BOOLEAN) {
                if (type == XPathType.STRING && OPTIONS.get(3)) return true;
                if (type == XPathType.NUMBER && OPTIONS.get(11)) return true;
            }
            return false;
        }
    }
}
