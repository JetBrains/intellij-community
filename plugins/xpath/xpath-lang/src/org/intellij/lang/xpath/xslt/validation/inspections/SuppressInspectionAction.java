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
package org.intellij.lang.xpath.xslt.validation.inspections;

import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlProlog;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class SuppressInspectionAction extends SuppressIntentionAction {
    private final String myToolId;
    private final @IntentionName String myMsg;

    SuppressInspectionAction(String toolId, @IntentionName String msg) {
        myToolId = toolId;
        myMsg = msg;
    }

    @Override
    @NotNull
    public String getText() {
        return myMsg;
    }

    @Override
    @NotNull
    public String getFamilyName() {
        return XPathBundle.message("intention.family.name.suppress.inspection");
    }

    @Nullable
    protected abstract XmlTag getAnchor(@NotNull PsiElement element);

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        return getAnchor(element) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        final XmlTag anchor = getAnchor(element);
        if (anchor == null) return;

        PsiElement prevSibling = anchor.getPrevSibling();
        while (prevSibling instanceof PsiWhiteSpace || prevSibling instanceof XmlText) {
            prevSibling = prevSibling.getPrevSibling();
        }
        if (prevSibling instanceof XmlProlog) {
            prevSibling = prevSibling.getLastChild();
            if (prevSibling != null && !(prevSibling instanceof XmlComment)) {
                prevSibling = PsiTreeUtil.getPrevSiblingOfType(prevSibling, XmlComment.class);
            }
        }
        if (prevSibling instanceof XmlComment comment) {
          final String text = comment.getCommentText();
            if (InspectionUtil.SUPPRESSION_PATTERN.matcher(text).matches()) {
                final String s = text.trim() + ", " + myToolId;
                final XmlComment newComment = createComment(project, s);
              CodeStyleManager.getInstance(PsiManager.getInstance(project).getProject()).reformat(comment.replace(newComment));
            } else {
                addNoinspectionComment(project, anchor);
            }
        } else {
            addNoinspectionComment(project, anchor);
        }
    }

    private void addNoinspectionComment(Project project, XmlTag anchor) throws IncorrectOperationException {
        final XmlComment newComment = createComment(project, "noinspection " + myToolId);
        PsiElement parent = anchor.getParentTag();
        if (parent == null) {
            parent = PsiTreeUtil.getPrevSiblingOfType(anchor, XmlProlog.class);
            if (parent != null) {
              CodeStyleManager.getInstance(PsiManager.getInstance(project).getProject()).reformat(parent.add(newComment));
            }
        } else {
          CodeStyleManager.getInstance(PsiManager.getInstance(project).getProject()).reformat(parent.addBefore(newComment, anchor));
        }
    }

    @NotNull
    private static XmlComment createComment(Project project, String s) throws IncorrectOperationException {
        final XmlTag element = XmlElementFactory.getInstance(project).createTagFromText("<foo><!-- " + s + " --></foo>", XMLLanguage.INSTANCE);
        final XmlComment newComment = PsiTreeUtil.getChildOfType(element, XmlComment.class);
        assert newComment != null;
        return newComment;
    }
}
