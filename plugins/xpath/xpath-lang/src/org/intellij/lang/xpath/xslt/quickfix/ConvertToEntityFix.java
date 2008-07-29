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

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.xpath.psi.XPathToken;
import org.intellij.lang.xpath.psi.impl.XPathChangeUtil;

public class ConvertToEntityFix extends AbstractFix {
    private final XPathToken myToken;
    private final String myValue;

    public ConvertToEntityFix(XPathToken token) {
        myToken = token;
        myValue = myToken.getText().replaceAll("<", "&lt;");
    }

    @NotNull
    public String getText() {
        return "Convert '" + myToken.getText() + "' to '" + myValue + "'";
    }

    public boolean isAvailableImpl(@NotNull Project project, Editor editor, PsiFile file) {
        if (!myToken.isValid()) {
            return false;
        }
        final PsiFile psiFile = myToken.getContainingFile();
        assert psiFile != null;
        final PsiElement context = psiFile.getContext();
        return context != null && context.isValid();
    }

    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        final XmlAttribute attribute = PsiTreeUtil.getContextOfType(myToken.getContainingFile(), XmlAttribute.class, true);
        assert attribute != null;

        final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(myToken.getLanguage());
        assert parserDefinition != null;

        final PsiFile f = XPathChangeUtil.createXPathFile(myToken, "1 " + myValue + " 2");

        final PsiElement firstChild = f.getFirstChild();
        assert firstChild != null;
//
        final XPathToken child = PsiTreeUtil.getChildOfType(firstChild, XPathToken.class);
        assert child != null;

        myToken.replace(child);
    }

    protected boolean requiresEditor() {
        return false;
    }
}