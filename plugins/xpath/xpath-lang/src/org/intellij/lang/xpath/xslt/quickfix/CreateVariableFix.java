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
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.CompleteMacro;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreateVariableFix extends AbstractFix {
    private final XPathVariableReference myReference;

    public CreateVariableFix(XPathVariableReference reference) {
        myReference = reference;
    }

    @Override
    public @NotNull String getText() {
        return XPathBundle.message("intention.name.create.variable", myReference.getReferencedName());
    }

    @Override
    public @NotNull String getFamilyName() {
        return XPathBundle.message("intention.family.name.create.variable");
    }

    @Override
    public void invoke(final @NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
        editor = editor == null ? null : InjectedLanguageEditorUtil.getTopLevelEditor(editor);

        XmlTag tag = PsiTreeUtil.getContextOfType(myReference, XmlTag.class, true);
        if (tag == null) return;

        XmlTag xmlTag = tag.createChildTag("variable", XsltSupport.XSLT_NS, null, false);
        xmlTag.setAttribute("name", myReference.getReferencedName());
        xmlTag.setAttribute("select", "dummy");

        final XmlAttribute select = xmlTag.getAttribute("select", null);
        assert select != null;
        final PsiElement dummy = XsltSupport.getAttValueToken(select);
        assert dummy != null;

        final TemplateBuilderImpl builder = createTemplateBuilder(xmlTag);
        builder.replaceElement(dummy, new MacroCallNode(new CompleteMacro()));
        builder.setEndVariableAfter(select);
        final Template template = builder.buildTemplate();
        template.addTextSegment("\n");
        template.setToIndent(true);


        final XmlTag insertionPoint = findVariableInsertionPoint(tag);
        moveTo(editor, insertionPoint);

        TemplateManager.getInstance(project).startTemplate(editor, template);
    }

    private XmlTag findVariableInsertionPoint(final XmlTag currentUsageTag) {
        return XsltCodeInsightUtil.findVariableInsertionPoint(currentUsageTag, getUsageBlock(), myReference.getReferencedName());
    }

    public @Nullable PsiElement getUsageBlock() {
        return XsltCodeInsightUtil.getUsageBlock(myReference);
    }

    @Override
    public boolean isAvailableImpl(@NotNull Project project, Editor editor, PsiFile file) {
      if (!myReference.isValid()) {
        return false;
      }
      final PsiFile psiFile = myReference.getContainingFile();
      assert psiFile != null;
      //noinspection ConstantValue -- rechecking of isValid is intended
      return myReference.isValid() && psiFile.isValid();
    }

    @Override
    protected boolean requiresEditor() {
        return true;
    }

    @Override
    public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
      return new CreateVariableFix(PsiTreeUtil.findSameElementInCopy(myReference, target));
    }
}