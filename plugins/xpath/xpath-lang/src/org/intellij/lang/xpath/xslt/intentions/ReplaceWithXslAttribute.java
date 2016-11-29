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
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 20.06.2007
 */
public class ReplaceWithXslAttribute implements IntentionAction {
    @NotNull
    public String getText() {
        return "Replace with 'xsl:attribute'";
    }

    @NotNull
    public String getFamilyName() {
        return "Replace with xsl:attribute";
    }

    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (!XsltSupport.isXsltFile(file)) return false;

        final int offset = editor.getCaretModel().getOffset();
        final PsiElement element = file.findElementAt(offset);
      final XmlAttribute attr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class, false);
        if (attr == null || attr.getValueElement() == null) {
            return false;
        }
        if (XsltSupport.isXsltTag(attr.getParent())) {
            return false;
        }
        
        final ASTNode node = attr.getNode();
        if (node == null) return false;
        final ASTNode nameNode = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(node);

        if (nameNode == null) {
            return false;
        } else {
            return nameNode.getTextRange().contains(offset);
        }
    }

    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        final int offset = editor.getCaretModel().getOffset();
        final PsiElement element = file.findElementAt(offset);
      final XmlAttribute attr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class, false);
        assert attr != null;

        final XmlAttributeValue valueElement = attr.getValueElement();
        assert valueElement != null;
        final String s = attr.getValueTextRange().substring(valueElement.getText());

        final List<Pair<String, Boolean>> chunks = new ArrayList<>();
        final StringBuilder builder = new StringBuilder(s.length());

        final PsiFile[] files = XsltSupport.getFiles(attr);
        int i=0, j=0;
        while (i < s.length()) {
            final char c = s.charAt(i++);
            if (c == '{' && j < files.length) {
                if (i < s.length() - 1 && s.charAt(i) != '{') {
                    final PsiFile f = files[j++];
                    if (builder.length() > 0) {
                        chunks.add(Pair.create(builder.toString(), Boolean.FALSE));
                        builder.setLength(0);
                    }
                    chunks.add(Pair.create(f.getText(), Boolean.TRUE));
                    i += f.getTextLength();
                    if (s.charAt(i) == '}') i++;
                } else {
                    builder.append(c);
                    i++;
                }
            } else {
                builder.append(c);
            }
        }
        if (builder.length() > 0) {
            chunks.add(Pair.create(builder.toString(), Boolean.FALSE));
        }

        final XmlTag parent = attr.getParent();
      final XmlTag attrTag = parent.createChildTag("attribute", XsltSupport.XSLT_NS, null, false);
        attrTag.setAttribute("name", attr.getName()); // local name?

        final String value = attr.getNamespace();
        if (value.length() > 0) {
            attrTag.setAttribute("namespace", value);
        }
        for (Pair<String, Boolean> chunk : chunks) {
            final XmlTag child;
            if (chunk.second) {
              child = parent.createChildTag("value-of", XsltSupport.XSLT_NS, null, false);
                child.setAttribute("select", chunk.first);
//            } else if (chunks.size() == 1) {
                // TODO: really? or always create an xsl:text?
//                attrTag.add(attrTag.getManager().getElementFactory().createDisplayText(chunk.first));
//                continue;
            } else {
              child = parent.createChildTag("text", XsltSupport.XSLT_NS, null, false);
                child.add(XmlElementFactory.getInstance(child.getProject()).createDisplayText(chunk.first));
            }
            attrTag.add(child);
        }
        final PsiElement child = XsltCodeInsightUtil.findFirstRealTagChild(parent);
        if (child != null) {
            parent.addBefore(attrTag, child);
        } else {
            parent.add(attrTag);
        }
        attr.delete();
    }

    public boolean startInWriteAction() {
        return true;
    }
}
