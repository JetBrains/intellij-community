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
package org.intellij.lang.xpath.xslt.refactoring.introduceParameter;

import com.intellij.lang.LanguageNamesValidation;

import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.xslt.refactoring.BaseIntroduceDialog;
import org.intellij.lang.xpath.xslt.refactoring.BaseIntroduceForm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class IntroduceParameterDialog extends BaseIntroduceDialog implements IntroduceParameterOptions {
    private static final String TITLE = "XSLT - Introduce Parameter";

    private JPanel myContentPane;
    private JCheckBox myCreateWithDefault;
    private BaseIntroduceForm myForm;

    private boolean myIsPreview;
    private final boolean myForceDefault;
    private final MyPreviewAction myPreviewAction = new MyPreviewAction();

    public IntroduceParameterDialog(XPathExpression expression, int numberOfExpressions, boolean forceDefault) {
        super(expression.getProject(), LanguageNamesValidation.INSTANCE.forLanguage(expression.getLanguage()));
        myForceDefault = forceDefault;
        init(expression, numberOfExpressions, TITLE);

        getOKAction().addPropertyChangeListener(new PropertyChangeListener() {
            @SuppressWarnings({"AutoUnboxing"})
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals("enabled")) {
                    myPreviewAction.setEnabled(((Boolean)evt.getNewValue()) && !myCreateWithDefault.isSelected());
                }
            }
        });
        if (forceDefault) {
            myCreateWithDefault.setSelected(true);
            myCreateWithDefault.setVisible(false);
        } else {
            myCreateWithDefault.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    myPreviewAction.setEnabled(getOKAction().isEnabled() && !myCreateWithDefault.isSelected());
                }
            });
        }
        myPreviewAction.setEnabled(false);
    }

    @NotNull
    protected Action[] createActions() {
        return myForceDefault ? super.createActions() : new Action[]{ getOKAction(), getPreviewAction(), getCancelAction() };
    }

    private Action getPreviewAction() {
        return myPreviewAction;
    }

    protected JComponent createCenterPanel() {
        return myContentPane;
    }

    public boolean isCreateDefault() {
        return myCreateWithDefault.isSelected();
    }

    public boolean isReplaceAll() {
        return myForm.isReplaceAll();
    }

    public boolean isPreview() {
        return myIsPreview;
    }

    protected BaseIntroduceForm getForm() {
        return myForm;
    }

    private class MyPreviewAction extends AbstractAction {
        public MyPreviewAction() {
            super("&Preview");
        }

        public void actionPerformed(ActionEvent e) {
            myIsPreview = true;
            doOKAction();
        }
    }
}
