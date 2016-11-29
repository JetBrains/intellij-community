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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.*;
import org.intellij.lang.xpath.xslt.quickfix.AddWithParamFix;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

// Just a clever trick that makes use of the already existing quickfix and the completion for missing arguments.
public class AddOptionalArgumentsIntention extends AddWithParamFix {

    @NotNull
    public String getFamilyName() {
        return "Add optional Argument(s)";
    }

    @NotNull
    public String getText() {
        return getFamilyName();
    }

    public boolean isAvailableImpl(@NotNull Project project, Editor editor, PsiFile file) {
        if (!XsltSupport.isXsltFile(file)) return false;

        final int offset = editor.getCaretModel().getOffset();
        final PsiElement element = file.findElementAt(offset);
        if (element == null) return false;
        final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
        if (tag == null) return false;
        if (!XsltSupport.isTemplateCall(tag)) return false;

        final XsltCallTemplate call = XsltElementFactory.getInstance().wrapElement(tag, XsltCallTemplate.class);
        if (call == null) return false;

        final XsltTemplate template = call.getTemplate();
        if (template == null) return false;

        final Set<String> params = new HashSet<>();
        final XsltParameter[] parameters = template.getParameters();
        for (XsltParameter parameter : parameters) {
            if (parameter.hasDefault()) params.add(parameter.getName());
        }
        final XsltWithParam[] arguments = call.getArguments();
        for (XsltWithParam argument : arguments) {
            params.remove(argument.getParamName());
        }

        myTag = tag;
        return params.size() > 0 && isAvailableAt(element, tag, offset);
    }

    protected static boolean isAvailableAt(PsiElement element, XmlTag tag, int offset) {
        final ASTNode node = tag.getNode();
        if (node != null) {
            final ASTNode child = XmlChildRole.START_TAG_NAME_FINDER.findChild(node);
            if (child != null && child.getTextRange().contains(offset)) {
                return true;
            }
        }

//        final XmlAttribute att = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
//        if (att != null && "name".equals(att.getName()) && att.getValueElement() != null) {
//            return att.getValueElement().getTextRange().contains(offset);
//        }

        return false;
    }
}