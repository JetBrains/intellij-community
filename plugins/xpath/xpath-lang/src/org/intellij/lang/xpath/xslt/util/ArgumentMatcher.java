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

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltTemplateInvocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArgumentMatcher implements ResolveUtil.Matcher {
    private final XsltTemplateInvocation myCall;

    public ArgumentMatcher(@NotNull XsltTemplateInvocation call) {
        myCall = call;
    }

    @Override
    public XmlTag getRoot() {
        return myCall.getTag();
    }

    @Override
    public boolean isRecursive() {
        return false;
    }

    @Override
    @Nullable
    public Result match(XmlTag element) {
        if (element.getLocalName().equals("with-param") && XsltSupport.isXsltTag(element)) {
            return Result.create(transform(element));
        }
        return null;
    }

    protected PsiElement transform(XmlTag element) {
        return element;
    }

    @Override
    public ResolveUtil.Matcher variantMatcher() {
        throw new UnsupportedOperationException();
    }
}
