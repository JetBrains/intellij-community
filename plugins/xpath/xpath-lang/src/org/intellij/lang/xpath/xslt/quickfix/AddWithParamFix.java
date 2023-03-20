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

import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.CompleteMacro;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltParameter;
import org.intellij.lang.xpath.xslt.refactoring.RefactoringUtil;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;

public class AddWithParamFix extends AbstractFix {
    protected XmlTag myTag;
    private final String myName;

    protected AddWithParamFix() {
        myName = null;
    }

    public AddWithParamFix(XsltParameter parameter, XmlTag tag) {
        myTag = tag;
        myName = parameter.getName();
    }

    @Override
    @NotNull
    public String getText() {
        return XPathBundle.message("intention.name.add.argument.for.x", myName);
    }

    @Override
    public @NotNull String getFamilyName() {
        return XPathBundle.message("intention.family.name.add.argument");
    }

    @Override
    public boolean isAvailableImpl(@NotNull Project project, Editor editor, PsiFile file) {
        return myTag.isValid();
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    protected boolean requiresEditor() {
        return true;
    }

    @Override
    public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
        SmartPsiElementPointer<XmlTag> result = WriteAction.compute(() -> {
            final XmlTag withParamTag = RefactoringUtil.addWithParam(myTag);

            withParamTag.setAttribute("name", myName != null ? myName : "dummy");
            withParamTag.setAttribute("select", "dummy");
            return SmartPointerManager.getInstance(project).
              createSmartPsiElementPointer(withParamTag);
        });

        final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        final Document doc = psiDocumentManager.getDocument(file);
        assert doc != null;
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(doc);

        final XmlTag withParamTag = result.getElement();
        assert withParamTag != null;

        final TemplateBuilderImpl builder = new TemplateBuilderImpl(withParamTag);
        final XmlAttribute selectAttr = withParamTag.getAttribute("select", null);
        assert selectAttr != null;
        PsiElement dummy = XsltSupport.getAttValueToken(selectAttr);
        builder.replaceElement(dummy, new MacroCallNode(new CompleteMacro()));

        if (myName == null) {
            final XmlAttribute nameAttr = withParamTag.getAttribute("name", null);
            assert nameAttr != null;
            dummy = XsltSupport.getAttValueToken(nameAttr);
            builder.replaceElement(dummy, new MacroCallNode(new CompleteMacro()));
        }

        moveTo(editor, withParamTag);

        WriteAction.run(() -> {
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
            final TemplateManager mgr = TemplateManager.getInstance(myTag.getProject());
            mgr.startTemplate(editor, builder.buildInlineTemplate());
        });
    }
}
