/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.intellij.lang.xpath.xslt.XsltSupport;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 10.06.2010
*/
public final class XsltImplicitUsagesProvider implements ImplicitUsageProvider {
    public boolean isImplicitUsage(PsiElement element) {
        if (!(element instanceof XmlAttribute)) {
            return false;
        }
        final XmlAttribute attr = (XmlAttribute)element;
        if (!attr.isNamespaceDeclaration()) {
            return false;
        }

        if (!XsltSupport.isXsltFile(attr.getContainingFile())) {
            return false;
        }

        // This need to catch both prefix references from injected XPathFiles and prefixes from mode declarations/references:
        // <xsl:template match="*" mode="prefix:name" />
        
        // BTW: Almost the same logic applies to other XML dialects (RELAX-NG).
        // Pull this class into the platform?
        final String prefix = attr.getLocalName();
        final SchemaPrefix target = new SchemaPrefix(attr, TextRange.from("xmlns:".length(), prefix.length()), prefix);
        final Query<PsiReference> q = ReferencesSearch.search(target, new LocalSearchScope(attr.getParent()));
        return !q.forEach(new Processor<PsiReference>() {
            public boolean process(PsiReference psiReference) {
                if (psiReference.getElement() == attr) {
                    return true;
                }
                return false;
            }
        });
    }

    public boolean isImplicitRead(PsiElement element) {
        return false;
    }

    public boolean isImplicitWrite(PsiElement element) {
        return false;
    }
}