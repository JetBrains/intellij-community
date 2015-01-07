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
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

public class CreateParameterFix extends AddParamBase {
    private final XPathVariableReference myReference;

    public CreateParameterFix(XPathVariableReference reference) {
        myReference = reference;
    }

    @NotNull
    public String getText() {
        return "Create parameter '" + myReference.getReferencedName() + "'";
    }

    protected String getParamName() {
        return myReference.getReferencedName();
    }

    protected XmlTag findTemplateTag() {
        return XsltCodeInsightUtil.getTemplateTag(myReference, true);
    }

    public boolean isAvailableImpl(@NotNull Project project, Editor editor, PsiFile file) {
        return myReference.isValid() && XsltCodeInsightUtil.getTemplateTag(myReference, true) != null;
    }
}