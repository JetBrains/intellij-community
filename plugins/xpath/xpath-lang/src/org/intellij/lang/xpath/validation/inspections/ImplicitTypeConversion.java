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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.Alarm;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathFunctionCall;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.validation.ExpectedTypeUtil;
import org.intellij.lang.xpath.validation.inspections.quickfix.XPathQuickFixFactory;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.BitSet;

// TODO: Option to flag literals: <number> = '123', <string> = 123, etc.
public class ImplicitTypeConversion extends XPathInspection {
    @NonNls
    private static final String SHORT_NAME = "ImplicitTypeConversion";

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

    @NotNull
    public String getDisplayName() {
        return "Implicit Type Conversion";
    }

    @NotNull
    @NonNls
    public String getShortName() {
        return SHORT_NAME;
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    protected Visitor createVisitor(InspectionManager manager, boolean isOnTheFly) {
        return new MyElementVisitor(manager, isOnTheFly);
    }

    @Nullable
    public JComponent createOptionsPanel() {
        return new Options();
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

    protected boolean acceptsLanguage(Language language) {
      return language == XPathFileType.XPATH.getLanguage();
    }

    final class MyElementVisitor extends Visitor {
        MyElementVisitor(InspectionManager manager, boolean isOnTheFly) {
            super(manager, isOnTheFly);
        }

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

                addProblem(myManager.createProblemDescriptor(expression,
                        "Expression should be of type '" + type.getName() + "'", myOnTheFly, fixes,
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
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

    public class Options extends JPanel {
        @SuppressWarnings({ "UNUSED_SYMBOL", "FieldCanBeLocal" })
        private JPanel root;
        private JCheckBox NS_S;
        private JCheckBox NS_N;
        private JCheckBox NS_B;
        private JCheckBox S_S;
        private JCheckBox S_N;
        private JCheckBox S_B;
        private JCheckBox N_S;
        private JCheckBox N_N;
        private JCheckBox N_B;
        private JCheckBox B_S;
        private JCheckBox B_N;
        private JCheckBox B_B;
        private JCheckBox myAlwaysFlagExplicitConversion;
        private JCheckBox myIgnoreNodesetToString;

        private final JCheckBox[][] matrix = new JCheckBox[][]{
                {NS_S, S_S, N_S, B_S},
                {NS_N, S_N, N_N, B_N},
                {NS_B, S_B, N_B, B_B},
        };

        public Options() {
            for (int i = 0; i < matrix.length; i++) {
                JCheckBox[] row = matrix[i];
                for (int j = 0; j < row.length; j++) {
                    JCheckBox to = row[j];
                    final int index = row.length * i + j;
                    to.setSelected(OPTIONS.get(index));
                    to.addItemListener(new ItemListener() {
                        public void itemStateChanged(ItemEvent e) {
                            OPTIONS.set(index, e.getStateChange() == ItemEvent.SELECTED);
                        }
                    });
                    if (j == i + 1) to.setEnabled(false);
                }
            }
            myAlwaysFlagExplicitConversion.setSelected(FLAG_EXPLICIT_CONVERSION);
            myAlwaysFlagExplicitConversion.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    FLAG_EXPLICIT_CONVERSION = e.getStateChange() == ItemEvent.SELECTED;
                }
            });
            myIgnoreNodesetToString.setSelected(IGNORE_NODESET_TO_BOOLEAN_VIA_STRING);
            myIgnoreNodesetToString.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    IGNORE_NODESET_TO_BOOLEAN_VIA_STRING = e.getStateChange() == ItemEvent.SELECTED;
                }
            });
        }

        public void setEnabled(final boolean enabled) {
            super.setEnabled(enabled);
            new Alarm().addRequest(() -> {
                for (int i = 0; i < matrix.length; i++) {
                    JCheckBox[] row = matrix[i];
                    for (int j = 0; j < row.length; j++) {
                        JCheckBox to = row[j];
                        to.setEnabled(enabled && j != i + 1);
                    }
                }
            }, 200);
        }

        private void createUIComponents() {
            root = this;
        }
    }
}
