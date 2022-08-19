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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddParameterFix extends AddParamBase {
    private final String myName;
    private final XsltTemplate myTemplate;

    public AddParameterFix(String name, XsltTemplate template) {
        myName = name;
        myTemplate = template;
    }

    @Override
    @NotNull
    public String getText() {
        return XPathBundle.message("intention.name.add.parameter.to.template", myName, myTemplate.getName());
    }

    @Override
    public @NotNull String getFamilyName() {
        return XPathBundle.message("intention.family.name.add.parameter.to.template");
    }

    @Override
    protected String getParamName() {
        return myName;
    }

    @Override
    @Nullable
    protected XmlTag findTemplateTag() {
        return PsiTreeUtil.getParentOfType(myTemplate.getNavigationElement(), XmlTag.class, false);
    }

    @Override
    public boolean isAvailableImpl(@NotNull Project project, Editor editor, PsiFile file) {
        return myTemplate.isValid();
    }

    @Override
    public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
      return new AddParameterFix(myName, PsiTreeUtil.findSameElementInCopy(myTemplate, target));
    }
}