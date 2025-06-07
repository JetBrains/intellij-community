/*
 * Copyright 2002-2007 Sascha Weinreuter
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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InlineXslAttribute implements IntentionAction {
    @Override
    public @NotNull String getText() {
        return getFamilyName();
    }

    @Override
    public @NotNull String getFamilyName() {
        return XPathBundle.message("intention.family.name.inline.xsl.attribute");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
        if (!XsltSupport.isXsltFile(psiFile)) return false;

        final int offset = editor.getCaretModel().getOffset();
        final PsiElement element = psiFile.findElementAt(offset);
      final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
        if (tag == null) {
            return false;
        }
        if (!XsltSupport.isXsltTag(tag)) {
            return false;
        } else if (!"attribute".equals(tag.getLocalName())) {
            return false;
        } else if (tag.getAttribute("name") == null) {
            return false;
        } else if (findParent(tag) == null) {
            // we cannot "inline" anything that isn't clearly the child of either an xsl:element or a literal result element
            return false;
        }

        final ASTNode node = tag.getNode();
        if (node == null) return false;
        final ASTNode nameNode = XmlChildRole.START_TAG_NAME_FINDER.findChild(node);

        if (nameNode == null || !nameNode.getTextRange().contains(offset)) {
            return false;
        }

        final XmlTag[] texts = tag.findSubTags("text", XsltSupport.XSLT_NS);
        final XmlTag[] exprs = tag.findSubTags("value-of", XsltSupport.XSLT_NS);
        final PsiElement[] children = tag.getChildren();
        for (PsiElement child : children) {
            if (child instanceof XmlText text) {
              if (text.getText().trim().isEmpty()) {
                    if (texts.length == 0 && exprs.length == 0) {
                        return false;
                    }
                }
            } else if (child instanceof XmlTag t) {
              if (XsltSupport.isXsltTag(t)) {
                    if ("text".equals(t.getLocalName())) {

                    } else if ("value-of".equals(t.getLocalName())) {
                        if (t.getAttribute("select") == null) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
        final int offset = editor.getCaretModel().getOffset();
        final PsiElement element = psiFile.findElementAt(offset);
      final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
        assert tag != null;

        final StringBuilder sb = new StringBuilder();
        final PsiElement[] children = tag.getChildren();
        for (PsiElement child : children) {
            if (child instanceof XmlText text) {
              if (!text.getText().trim().isEmpty()) {
                    sb.append(text.getText().replaceAll("\"", "&quot;"));
                }
            } else if (child instanceof XmlTag t) {
              if (XsltSupport.isXsltTag(t)) {
                    if ("text".equals(t.getLocalName())) {
                        sb.append(t.getValue().getText().replaceAll("\"", "&quot;"));
                    } else if ("value-of".equals(t.getLocalName())) {
                        sb.append("{").append(t.getAttributeValue("select")).append("}");
                    } else {
                        assert false;
                    }
                }
            }
        }

        final XmlTag p = findParent(tag);
        if (p != null) {
            final String value = tag.getAttributeValue("name");
            p.setAttribute(value, sb.toString());

            // TODO: deal with namespace prefix mapping

            tag.delete();
        }
    }

    private static @Nullable XmlTag findParent(XmlTag tag) {
        XmlTag p = tag.getParentTag();
        if (p == null) {
            return null;
        }
        return !XsltSupport.isXsltTag(p) || "element".equals(p.getLocalName()) ? p : null;
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}