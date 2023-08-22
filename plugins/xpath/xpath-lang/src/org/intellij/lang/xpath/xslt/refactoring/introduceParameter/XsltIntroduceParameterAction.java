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

import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.xslt.refactoring.BaseIntroduceAction;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.intellij.plugins.xpathView.XPathBundle;

import java.util.List;
import java.util.Set;

public class XsltIntroduceParameterAction extends BaseIntroduceAction<IntroduceParameterOptions> {
    @Override
    public String getRefactoringName() {
        return XPathBundle.message("dialog.title.introduce.parameter");
    }

    @Override
    protected String getCommandName() {
        return XPathBundle.message("command.name.introduce.xslt.parameter");
    }

    @Override
    protected IntroduceParameterOptions getSettings(XPathExpression expression, Set<XPathExpression> matchingExpressions) {
        final boolean forceDefault = XsltCodeInsightUtil.getTemplateTag(expression, true, true) == null;
        final IntroduceParameterDialog dialog = new IntroduceParameterDialog(expression, matchingExpressions.size() + 1, forceDefault);
        dialog.show();
        return dialog;
    }

    @Override
    protected boolean extractImpl(XPathExpression expression, Set<XPathExpression> matchingExpressions, List<XmlTag> otherMatches, IntroduceParameterOptions settings) {
        new IntroduceParameterProcessor(expression.getProject(), expression, matchingExpressions, settings).run();
        return false;
    }
}
