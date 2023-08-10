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
package org.intellij.lang.xpath.xslt.quickfix;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.psi.XPathBinaryExpression;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathToken;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.lang.xpath.psi.impl.XPathChangeUtil;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlipOperandsFix extends AbstractFix {
    private final XPathBinaryExpression myExpression;
    private final XPathToken myToken;

    public FlipOperandsFix(XPathToken token) {
        myToken = token;
        myExpression = PsiTreeUtil.getParentOfType(token, XPathBinaryExpression.class);
    }

    @Override
    @NotNull
    public String getText() {
        return XPathBundle.message("intention.name.flip.binary.expression.to", myToken.getText(), myToken.getText().replace('<', '>'));
    }

    @Override
    public @NotNull String getFamilyName() {
        return XPathBundle.message("intention.family.name.flip.binary.expression");
    }

    @Override
    public boolean isAvailableImpl(@NotNull Project project, Editor editor, PsiFile file) {
        return myExpression != null && myExpression.isValid() && myExpression.getType() == XPathType.BOOLEAN && myExpression.getROperand() != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        final XmlAttribute attribute = PsiTreeUtil.getContextOfType(myToken, XmlAttribute.class, true);
        assert attribute != null;

        final PsiFile f = XPathChangeUtil.createXPathFile(myToken,
                getOperandText(myExpression.getROperand()) + " " +
                        myToken.getText().replace('<', '>') + " " +
                        getOperandText(myExpression.getLOperand())
        );

        final PsiElement firstChild = f.getFirstChild();
        assert firstChild != null;

        myExpression.replace(firstChild);
    }

    private static String getOperandText(XPathExpression operand) {
        return operand != null ? operand.getText() : "";
    }

    @Override
    protected boolean requiresEditor() {
        return false;
    }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new FlipOperandsFix(PsiTreeUtil.findSameElementInCopy(myToken, target));
  }
}
