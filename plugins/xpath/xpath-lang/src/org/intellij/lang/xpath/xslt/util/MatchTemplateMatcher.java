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

import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;

import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;

public class MatchTemplateMatcher extends TemplateMatcher {
    protected final @Nullable QName myMode;

    public MatchTemplateMatcher(@NotNull XmlDocument document, @Nullable QName mode) {
        super(document);
        myMode = mode;
    }

    protected ResolveUtil.Matcher changeDocument(XmlDocument document) {
        return new MatchTemplateMatcher(document, myMode);
    }

    public ResolveUtil.Matcher variantMatcher() {
        return new MatchTemplateMatcher(myDocument, myMode);
    }

    public boolean matches(XmlTag element) {
        if (super.matches(element) && element.getAttribute("match", null) != null) {
            final XsltTemplate t = XsltElementFactory.getInstance().wrapElement(element, XsltTemplate.class);
            final QName mode = t.getMode();
            if (mode != null) {
                if (QNameUtil.equal(myMode, mode)) {
                    return true;
                }
            } else {
                return myMode == null;
            }
        }
        return false;
    }
}
