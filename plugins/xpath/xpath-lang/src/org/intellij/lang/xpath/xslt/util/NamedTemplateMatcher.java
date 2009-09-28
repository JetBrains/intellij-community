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
import org.intellij.lang.xpath.xslt.XsltSupport;

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;

public class NamedTemplateMatcher extends TemplateMatcher {
    protected final String myName;

    public NamedTemplateMatcher(XmlDocument document, String name) {
        super(document);
        myName = name;
    }

    protected boolean matches (XmlTag element) {
        if (XsltSupport.isTemplate(element, true)) {
            final XmlAttribute attribute = element.getAttribute("name", null);
            if (myName == null || (attribute != null && myName.equals(attribute.getValue()))) {
                return true;
            }
        }
        return false;
    }

    protected ResolveUtil.Matcher changeDocument(XmlDocument document) {
        return new NamedTemplateMatcher(document, myName);
    }

    public ResolveUtil.Matcher variantMatcher() {
        return new NamedTemplateMatcher(myDocument, null);
    }
}
