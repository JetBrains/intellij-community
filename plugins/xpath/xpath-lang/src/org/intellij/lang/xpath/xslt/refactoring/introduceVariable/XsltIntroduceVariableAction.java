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

import com.intellij.psi.codeStyle.CodeStyleManager;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.psi.impl.XPathChangeUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.refactoring.BaseIntroduceAction;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.ASTNode;

import java.util.List;
import java.util.Set;

@SuppressWarnings({"ComponentNotRegistered"})
public class XsltIntroduceVariableAction extends BaseIntroduceAction<IntroduceVariableOptions> {

    public String getRefactoringName() {
        return "Introduce Variable";
    }

    protected String getCommandName() {
        return "Introduce XSLT Variable";
    }

    protected boolean extractImpl(XPathExpression expression, Set<XPathExpression> matchingExpressions, List<XmlTag> otherMatches, IntroduceVariableOptions dlg) {
        final XmlAttribute attribute = PsiTreeUtil.getContextOfType(expression, XmlAttribute.class, true);
        assert attribute != null;

        try {
            final String name = dlg.getName();
            final XmlTag insertionPoint = XsltCodeInsightUtil.findVariableInsertionPoint(
                    attribute.getParent(),
                    XsltCodeInsightUtil.getUsageBlock(expression),
                    name,
                    dlg.isReplaceAll() ? otherMatches.toArray(new XmlTag[otherMatches.size()]) : XmlTag.EMPTY);

            final XmlTag parentTag = insertionPoint.getParentTag();
            assert parentTag != null : "Could not locate position to create variable at";

            final XmlTag xmlTag = parentTag.createChildTag("variable", XsltSupport.XSLT_NS, null, false);
            xmlTag.setAttribute("name", name);
            xmlTag.setAttribute("select", expression.getText());

            // TODO: revisit the formatting
            final PsiElement element = parentTag.addBefore(xmlTag, insertionPoint);
            final ASTNode node1 = parentTag.getNode();
            assert node1 != null;
            final ASTNode node2 = element.getNode();
            assert node2 != null;
          CodeStyleManager.getInstance(xmlTag.getManager().getProject()).reformatNewlyAddedElement(node1, node2);

            final XPathVariableReference var = XPathChangeUtil.createVariableReference(expression, name);
            expression.replace(var);

            if (dlg.isReplaceAll()) {
                for (XPathExpression expr : matchingExpressions) {
                    expr.replace(XPathChangeUtil.createVariableReference(expr, name));
                }
                return false;
            } else {
                return true;
            }
        } catch (IncorrectOperationException e) {
            Logger.getInstance(getClass().getName()).error(e);
            return false;
        }
    }

    protected IntroduceVariableOptions getSettings(XPathExpression expression, Set<XPathExpression> matchingExpressions) {
        final IntroduceVariableDialog dlg = new IntroduceVariableDialog(expression, matchingExpressions.size() + 1);
        dlg.show();
        return dlg;
    }
}
