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

import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.psi.impl.ResolveUtil;

import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;

public class TemplateMatcher extends IncludeAwareMatcher {
    public TemplateMatcher(XmlDocument document) {
        super(document);
    }

    protected boolean matches(XmlTag element) {
        return XsltSupport.isTemplate(element, false);
    }

    protected ResolveUtil.Matcher changeDocument(XmlDocument document) {
        return new TemplateMatcher(document);
    }

    public ResolveUtil.Matcher variantMatcher() {
        return new TemplateMatcher(myDocument);
    }
}
