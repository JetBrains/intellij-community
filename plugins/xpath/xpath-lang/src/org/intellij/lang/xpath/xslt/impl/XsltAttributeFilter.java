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
package org.intellij.lang.xpath.xslt.impl;

import org.intellij.lang.xpath.xslt.XsltSupport;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.xml.XmlAttribute;

class XsltAttributeFilter implements ElementFilter {
    public boolean isAcceptable(Object object, PsiElement element) {
        if (!(object instanceof PsiElement)) return false;

        final PsiFile containingFile = ((PsiElement)object).getContainingFile();
        if (containingFile == null) return false;

        if (!XsltSupport.isXsltFile(containingFile)) return false;

        PsiElement psielement1 = ((PsiElement)object).getParent();
        if (psielement1 instanceof XmlAttribute || (psielement1 = psielement1.getParent()) instanceof XmlAttribute) {
            final XmlAttribute xmlattribute = (XmlAttribute)psielement1;
            if (XsltSupport.isTemplateCallName(xmlattribute)) {
                return true;
            }
            if (XsltSupport.isTemplateCallParamName(xmlattribute)) {
                return true;
            }
            if (XsltSupport.isVariableOrParamName(xmlattribute) || XsltSupport.isTemplateName(xmlattribute)) {
                return true;
            }
            if (XsltSupport.isIncludeOrImportHref(xmlattribute)) {
                return true;
            }
            if (XsltSupport.isMode(xmlattribute)) {
                return true;
            }
            if (XsltSupport.isFunctionName(xmlattribute)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public boolean isClassAcceptable(Class aClass) {
        return true;
    }
}
