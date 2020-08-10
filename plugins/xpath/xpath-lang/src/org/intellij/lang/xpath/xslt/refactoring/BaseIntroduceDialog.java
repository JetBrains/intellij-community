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
package org.intellij.lang.xpath.xslt.refactoring;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.openapi.util.NlsContexts;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.xslt.util.NameValidator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.text.MessageFormat;

public abstract class BaseIntroduceDialog extends DialogWrapper implements RefactoringOptions {
    protected final InputValidator myInputValidator;

    public BaseIntroduceDialog(Project project, NamesValidator validator) {
        super(project, false);
        myInputValidator = new NameValidator(project, validator);
    }

    @Override
    public boolean isCanceled() {
        return !isOK();
    }

    @Override
    protected void doOKAction() {
        if (myInputValidator.canClose(getName())) super.doOKAction();
    }

    protected void init(XPathExpression expression, int numberOfExpressions, @NlsContexts.DialogTitle String title) {
        setModal(true);
        setTitle(title);

        final JLabel jLabel = getTypeLabel();
        final String name = expression.getType().getName();
        jLabel.setText(name);

        final JCheckBox jCheckBox = getReplaceAll();
        if (numberOfExpressions > 1) {
            //noinspection HardCodedStringLiteral
            jCheckBox.setText(MessageFormat.format(jCheckBox.getText(), String.valueOf(numberOfExpressions)));
        } else {
            jCheckBox.setVisible(false);
        }

        getNameField().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                getOKAction().setEnabled(myInputValidator.checkInput(getName()));
            }
        });

        getOKAction().setEnabled(false);

        init();
    }

    private JCheckBox getReplaceAll() {
        return getForm().myReplaceAll;
    }

    private JLabel getTypeLabel() {
        return getForm().myTypeLabel;
    }

    private TextFieldWithHistory getNameField() {
        return getForm().myNameField;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return getForm().myNameField;
    }

    @Override
    @NlsSafe
    public String getName() {
        return getNameField().getText();
    }

    protected abstract BaseIntroduceForm getForm();
}
