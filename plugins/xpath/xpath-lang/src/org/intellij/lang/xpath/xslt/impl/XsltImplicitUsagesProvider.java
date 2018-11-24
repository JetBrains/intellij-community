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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Query;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.xslt.XsltSupport;

public final class XsltImplicitUsagesProvider implements ImplicitUsageProvider {
    public boolean isImplicitUsage(PsiElement element) {
        if (!(element instanceof XmlAttribute)) {
            return false;
        }
        final XmlAttribute attr = (XmlAttribute)element;
        if (!attr.isNamespaceDeclaration()) {
            return false;
        }
        final PsiFile file = attr.getContainingFile();
        if (!(file instanceof XmlFile)) {
          return false;
        }

        // also catch namespace declarations in "normal" XML files that have XPath injected into some attributes
        // ContextProvider.hasXPathInjections() is an optimization that avoids to run the references search on totally XPath-free XML files
        if (!ContextProvider.hasXPathInjections((XmlFile)file) && !XsltSupport.isXsltFile(file)) {
            return false;
        }

        // This need to catch both prefix references from injected XPathFiles and prefixes from mode declarations/references:
        // <xsl:template match="*" mode="prefix:name" />
        
        // BTW: Almost the same logic applies to other XML dialects (RELAX-NG).
        // Pull this class into the platform?
        final String prefix = attr.getLocalName();
        final SchemaPrefix target = new SchemaPrefix(attr, TextRange.from("xmlns:".length(), prefix.length()), prefix);
        final Query<PsiReference> q = ReferencesSearch.search(target, new LocalSearchScope(attr.getParent()));
        return !q.forEach(psiReference -> {
            if (psiReference.getElement() == attr) {
                return true;
            }
            return false;
        });
    }

    public boolean isImplicitRead(PsiElement element) {
        return false;
    }

    public boolean isImplicitWrite(PsiElement element) {
        return false;
    }
}
