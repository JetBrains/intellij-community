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
import com.intellij.util.BitUtil;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathFunctionCall;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.validation.ExpectedTypeUtil;
import org.intellij.lang.xpath.validation.inspections.quickfix.XPathQuickFixFactory;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.group;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.separator;

// TODO: Option to flag literals: <number> = '123', <string> = 123, etc.
public class ImplicitTypeConversion extends XPathInspection {
  private static final @NonNls String SHORT_NAME = "ImplicitTypeConversion";
  private static final String STRING = "STRING";
  private static final String NODESET = "NODESET";
  private static final String NUMBER = "NUMBER";
  private static final String BOOLEAN = "BOOLEAN";
  private static final int OPTION_COUNT = 12;

  public long BITS = 1720;

  public boolean FLAG_EXPLICIT_CONVERSION = true;
  public boolean IGNORE_NODESET_TO_BOOLEAN_VIA_STRING = true;

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
    return pane(group(XPathBundle.message("label.to", STRING), checkbox("0", XPathBundle.message("label.from", NODESET)),
                      checkbox("2", XPathBundle.message("label.from", NUMBER)),
                      checkbox("3", XPathBundle.message("label.from", BOOLEAN))).prefix("c"),
                group(XPathBundle.message("label.to", NUMBER), checkbox("4", XPathBundle.message("label.from", NODESET)),
                      checkbox("5", XPathBundle.message("label.from", STRING)),
                      checkbox("7", XPathBundle.message("label.from", BOOLEAN))).prefix("c"),
                group(XPathBundle.message("label.to", BOOLEAN), checkbox("8", XPathBundle.message("label.from", NODESET)),
                      checkbox("9", XPathBundle.message("label.from", STRING)),
                      checkbox("10", XPathBundle.message("label.from", NUMBER))).prefix("c"), separator(),
                checkbox("FLAG_EXPLICIT_CONVERSION", XPathBundle.message("checkbox.always.flag.explicit.conversion.to.unexpected.type")),
                checkbox("IGNORE_NODESET_TO_BOOLEAN_VIA_STRING",
                         XPathBundle.message("checkbox.ignore.conversion.of.nodeset.to.boolean.by.string.conversion")));
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return super.getOptionController().onPrefix("c", bindId -> isOptionSet(Integer.parseInt(bindId)), (bindId, value) -> {
      int index = Integer.parseInt(bindId);
      if (0 <= index && index < OPTION_COUNT) {
        BITS = BitUtil.set(BITS, 1 << index, (Boolean)value);
      }
    });
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
        final boolean isExplicit = FLAG_EXPLICIT_CONVERSION && ExpectedTypeUtil.isExplicitConversion(expression);
        checkExpressionOfType(expression, expectedType, isExplicit);
      }
    }

    private void checkExpressionOfType(@NotNull XPathExpression expression, XPathType type, boolean explicit) {
      final XPathType exprType = expression.getType();
      if (exprType.isAbstract() || type.isAbstract()) return;

      if (exprType != type && (explicit || isCheckedConversion(exprType, type))) {
        if (explicit && exprType == XPathType.STRING && type == XPathType.BOOLEAN) {
          final XPathExpression expr = ExpectedTypeUtil.unparenthesize(expression);
          if (expr instanceof XPathFunctionCall &&
              IGNORE_NODESET_TO_BOOLEAN_VIA_STRING &&
              ((XPathFunctionCall)expr).getArgumentList()[0].getType() == XPathType.NODESET) {
            return;
          }
        }

        final LocalQuickFix[] fixes;
        if (type != XPathType.NODESET) {
          final XPathQuickFixFactory fixFactory = ContextProvider.getContextProvider(expression).getQuickFixFactory();
          explicit = explicit && !(exprType == XPathType.STRING && type == XPathType.BOOLEAN);
          fixes = fixFactory.createImplicitTypeConversionFixes(expression, type, explicit);
        }
        else {
          fixes = null;
        }

        addProblem(
          myManager.createProblemDescriptor(expression, XPathBundle.message("inspection.message.expression.should.be.type", type.getName()),
                                            myOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
    }

    private boolean isCheckedConversion(XPathType exprType, XPathType type) {
      if (exprType == XPathType.NODESET) {
        if (type == XPathType.STRING && isOptionSet(0)) return true;
        if (type == XPathType.NUMBER && isOptionSet(4)) return true;
        if (type == XPathType.BOOLEAN && isOptionSet(8)) return true;
      }
      else if (exprType == XPathType.STRING) {
        if (type == XPathType.NUMBER && isOptionSet(5)) return true;
        if (type == XPathType.BOOLEAN && isOptionSet(9)) return true;
      }
      else if (exprType == XPathType.NUMBER) {
        if (type == XPathType.STRING && isOptionSet(2)) return true;
        if (type == XPathType.BOOLEAN && isOptionSet(10)) return true;
      }
      else if (exprType == XPathType.BOOLEAN) {
        if (type == XPathType.STRING && isOptionSet(3)) return true;
        if (type == XPathType.NUMBER && isOptionSet(11)) return true;
      }
      return false;
    }
  }

  private boolean isOptionSet(int index) {
    return 0 <= index && index < OPTION_COUNT && BitUtil.isSet(BITS, 1 << index);
  }
}
