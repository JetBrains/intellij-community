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
import com.intellij.psi.xml.XmlTag;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ParamMatcher extends BaseMatcher {
    private final XmlTag myRoot;
    private final String myName;
    private final Set<String> myExcludedNames;

    @SuppressWarnings({"unchecked"})
    public ParamMatcher(XmlTag parent, String name) {
        myRoot = parent;
        myName = name;
        myExcludedNames = Collections.emptySet();
    }

    public ParamMatcher(XmlTag root, String[] excludedNames, String name) {
        myRoot = root;
        myName = name;
        myExcludedNames = new HashSet<>(Arrays.asList(excludedNames));
    }

    public XmlTag getRoot() {
        return myRoot;
    }

    protected boolean matches(XmlTag tag) {
        if (isApplicable(tag)) {
            final XmlAttribute attribute = tag.getAttribute("name", null);
            if (attribute != null && matches(attribute)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isApplicable(XmlTag tag) {
        return XsltSupport.isParam(tag);
    }

    private boolean matches(XmlAttribute attribute) {
        final String value = attribute.getValue();
        return (myName == null || myName.equals(value)) && !myExcludedNames.contains(value);
    }

    @SuppressWarnings({"CallToSimpleGetterFromWithinClass"})
    public ResolveUtil.Matcher variantMatcher() {
        return new ParamMatcher(getRoot(), null);
    }
}
