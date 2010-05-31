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

import com.intellij.codeInspection.*;
import com.intellij.lang.xml.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.*;
import com.intellij.util.*;
import com.intellij.xml.util.*;
import org.jetbrains.annotations.*;

abstract class SuppressInspectionAction extends SuppressIntentionAction {
    private final String myToolId;
    private final String myMsg;

    public SuppressInspectionAction(String toolId, String msg) {
        myToolId = toolId;
        myMsg = msg;
    }

    @NotNull
    public String getText() {
        return myMsg;
    }

    @NotNull
    public String getFamilyName() {
        return "Suppress Inspection";
    }

    @Nullable
    protected abstract XmlTag getAnchor(@NotNull PsiElement element);

    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        return getAnchor(element) != null;
    }

    public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
        if (element == null) return;

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
        if (prevSibling instanceof XmlComment) {
            final XmlComment comment = (XmlComment)prevSibling;
            final String text = XmlUtil.getCommentText(comment);
            if (text != null && InspectionUtil.SUPPRESSION_PATTERN.matcher(text).matches()) {
                final String s = text.trim() + ", " + myToolId;
                final XmlComment newComment = createComment(project, s);
                PsiManager.getInstance(project).getCodeStyleManager().reformat(comment.replace(newComment));
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
                PsiManager.getInstance(project).getCodeStyleManager().reformat(parent.add(newComment));
            }
        } else {
            PsiManager.getInstance(project).getCodeStyleManager().reformat(parent.addBefore(newComment, anchor));
        }
    }

    @NotNull
    private static XmlComment createComment(Project project, String s) throws IncorrectOperationException {
        final XmlTag element = XmlElementFactory.getInstance(project).createTagFromText("<foo><!-- " + s + " --></foo>", XMLLanguage.INSTANCE);
        final XmlComment newComment = PsiTreeUtil.getChildOfType(element, XmlComment.class);
        assert newComment != null;
        return newComment;
    }

    public boolean startInWriteAction() {
        return true;
    }
}
