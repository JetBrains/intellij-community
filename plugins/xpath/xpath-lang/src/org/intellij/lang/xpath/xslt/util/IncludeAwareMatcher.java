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
package org.intellij.lang.xpath.xslt.util;

import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.Nullable;

public abstract class IncludeAwareMatcher extends BaseMatcher {
    protected final XmlDocument myDocument;

    public IncludeAwareMatcher(XmlDocument document) {
        myDocument = document;
    }

    @Nullable
    public XmlTag getRoot() {
        return myDocument.getRootTag();
    }

    @Nullable
    public final Result match(XmlTag element) {
        if (XsltSupport.isIncludeOrImport(element)) {
            final XmlAttribute href = element.getAttribute("href", null);
            if (href != null) {
                final PsiFile psiFile = element.getContainingFile();
                assert psiFile != null;
                PsiFile f = psiFile.getOriginalFile();
                if (f == psiFile || f.getVirtualFile() == null) f = myDocument.getContainingFile();

                final PsiFile file = ResolveUtil.resolveFile(href, f);
                if (file instanceof XmlFile) {
                  return Result.create(changeDocument(((XmlFile)file).getDocument()));
                }
            }
        } else {
            return matchImpl(element);
        }
        return null;
    }

    @Nullable
    protected Result matchImpl(XmlTag element) {
        if (matches(element)) {
            return Result.create(transform(element));
        }
        return null;
    }

    protected abstract ResolveUtil.Matcher changeDocument(XmlDocument document);
}
