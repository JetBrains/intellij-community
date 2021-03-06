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
package org.intellij.lang.xpath.xslt.refactoring.introduceVariable;

import com.intellij.lang.LanguageNamesValidation;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.xslt.refactoring.BaseIntroduceDialog;
import org.intellij.lang.xpath.xslt.refactoring.BaseIntroduceForm;
import org.intellij.plugins.xpathView.XPathBundle;

import javax.swing.*;

public class IntroduceVariableDialog extends BaseIntroduceDialog implements IntroduceVariableOptions {

    private JPanel myContentPane;
    private BaseIntroduceForm myForm;

    public IntroduceVariableDialog(XPathExpression expression, int numberOfExpressions) {
        super(expression.getProject(), LanguageNamesValidation.INSTANCE.forLanguage(expression.getLanguage()));
        init(expression, numberOfExpressions, XPathBundle.message("dialog.title.xslt.introduce.variable"));
    }

    @Override
    protected JComponent createCenterPanel() {
        return myContentPane;
    }

    @Override
    public boolean isReplaceAll() {
        return myForm.isReplaceAll();
    }

    @Override
    protected BaseIntroduceForm getForm() {
        return myForm;
    }
}
