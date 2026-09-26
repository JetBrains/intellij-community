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
package org.intellij.lang.xpath.xslt.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;

public class ConvertIfToChooseIntention implements IntentionAction {
    @Override
    public @NotNull String getFamilyName() {
        return XPathBundle.message("intention.family.name.convert.if.to.choose");
    }

    @Override
    public @NotNull String getText() {
        return getFamilyName();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
        final int offset = editor.getCaretModel().getOffset();
        final PsiElement element = psiFile.findElementAt(offset);
        assert element != null;

        final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
        assert tag != null && tag.getLocalName().equals("if");

        final String test = tag.getAttributeValue("test");
        final String body = tag.getValue().getText();

        final XmlTag parentTag = tag.getParentTag();
        assert parentTag != null;

        final XmlTag chooseTag = parentTag.createChildTag("choose", XsltSupport.XSLT_NS, null, false);
        final XmlTag whenTag = parentTag.createChildTag("when", XsltSupport.XSLT_NS, body, false);
        final XmlTag otherwiseTag = parentTag.createChildTag("otherwise", XsltSupport.XSLT_NS, "\n\n", false);
        whenTag.setAttribute("test", test);
        chooseTag.add(whenTag);
        chooseTag.add(otherwiseTag);

      CodeStyleManager.getInstance(tag.getManager().getProject()).reformat(tag.replace(chooseTag));
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
        if (!XsltSupport.isXsltFile(psiFile)) return false;

        final int offset = editor.getCaretModel().getOffset();
        final PsiElement element = psiFile.findElementAt(offset);
        if (element == null) return false;
        final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
        if (tag == null || tag.getParentTag() == null) return false;
        if (!tag.getLocalName().equals("if") || !XsltSupport.isXsltTag(tag)) return false;
        if (tag.getAttributeValue("test") == null) return false;

        final ASTNode node = tag.getNode();
        if (node == null) return false;

        final ASTNode child = XmlChildRole.START_TAG_NAME_FINDER.findChild(node);
        return child != null && child.getTextRange().contains(offset);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}