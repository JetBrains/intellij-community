/*
 * Copyright 2007 Sascha Weinreuter
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

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.lang.xpath.xslt.validation.XsltValidator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class UnusedElementInspection extends XsltInspection {

    @Nls
    @NotNull
    public String getDisplayName() {
        return "Unused Variable/Parameter";
    }

    @NonNls
    @NotNull
    public String getShortName() {
        return "XsltUnusedDeclaration";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new MyVisitor(holder);
    }

    private static class MyVisitor extends XmlElementVisitor {
        private final ProblemsHolder myHolder;

        public MyVisitor(ProblemsHolder holder) {
            myHolder = holder;
        }

        @Override
        public void visitXmlAttribute(XmlAttribute attribute) {
            if (!XsltSupport.isVariableOrParamName(attribute)) {
                return;
            }

            final XmlTag tag = attribute.getParent();
            if (XsltSupport.isTopLevelElement(tag)) {
                return;
            }
            final XsltVariable variable = XsltElementFactory.getInstance().wrapElement(tag, XsltVariable.class);
            final String name = variable.getName();
            if (name == null || name.length() == 0) {
                return;
            }
            
            XsltValidator.checkUnusedVariable(variable, myHolder);
        }
    }
}
