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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

public class CreateTemplateFix extends AbstractFix {
    private static final String DUMMY_NS = "urn:x__dummy__";
    private static final String DUMMY_TAG = "<dummy xmlns='" + DUMMY_NS + "' />";

    private final XmlTag myTag;
    private final String myName;

    public CreateTemplateFix(XmlTag tag, String name) {
        myTag = tag;
        myName = name;
    }

    @NotNull
    public String getText() {
        return "Create Template '" + myName + "'";
    }

    public void invoke(@NotNull Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
        final XmlTag tag = XsltCodeInsightUtil.getTemplateTag(myTag, false);
        assert tag != null; // checked by isAvailable

        final XmlTag parentTag = tag.getParentTag();
        assert parentTag != null;
        
        XmlTag templateTag = parentTag.createChildTag("template", XsltSupport.XSLT_NS, DUMMY_TAG, false);
        templateTag.setAttribute("name", myName);

        final XmlTag[] arguments = myTag.findSubTags("with-param", XsltSupport.XSLT_NS);
        if (arguments.length > 0) {
            final XmlTag dummy = templateTag.findFirstSubTag("dummy");
            for (XmlTag arg : arguments) {
                final String argName = arg.getAttributeValue("name");
                if (argName != null) {
                    final XmlTag paramTag = parentTag.createChildTag("param", XsltSupport.XSLT_NS, null, false);
                    paramTag.setAttribute("name", argName);
                    templateTag.addBefore(paramTag, dummy);
                }
            }
        }

        // this is a bit ugly, but seems like the only way to get a newline between the closing tag of the previous and
        // the start tag of the newly created tag
        final XmlTag dummy1 = (XmlTag)parentTag.addAfter(XmlElementFactory.getInstance(project).createTagFromText(DUMMY_TAG), tag);
        templateTag = (XmlTag)parentTag.addAfter(templateTag, dummy1);
        templateTag = (XmlTag)tag.getManager().getCodeStyleManager().reformat(templateTag);

        final XmlTag dummy2 = templateTag.findFirstSubTag("dummy");

        // TODO: caret is now positioned at the start of the line, but should be at properly inted position
        moveTo(editor, dummy2);
        
        deleteTag(dummy1);
        deleteTag(dummy2);
    }

    private static void deleteTag(XmlTag dummy1) throws IncorrectOperationException {
        final XmlText text = XmlElementFactory.getInstance(dummy1.getProject()).createDisplayText(" ");
        final PsiElement e = dummy1.replace(text).getFirstChild();
        assert e != null;
        final PsiElement element = e.getFirstChild();
        assert element != null;
        final PsiElement cdataText = element.getNextSibling();
        assert cdataText != null;
        e.replace(cdataText);
    }

    public boolean isAvailableImpl(@NotNull Project project, Editor editor, PsiFile file) {
        return myTag.isValid() && XsltCodeInsightUtil.getTemplateTag(myTag, false) != null;
    }

    protected boolean requiresEditor() {
        return true;
    }
}