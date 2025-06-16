// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath.xslt.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.psi.XsltTemplateInvocation;
import org.intellij.lang.xpath.xslt.psi.XsltWithParam;
import org.intellij.lang.xpath.xslt.util.ArgumentMatcher;
import org.jetbrains.annotations.NotNull;

abstract class XsltTemplateInvocationBase extends XsltElementImpl implements XsltTemplateInvocation {
    XsltTemplateInvocationBase(XmlTag target) {
        super(target);
    }

    @Override
    public XsltWithParam @NotNull [] getArguments() {
        return convertArray(ResolveUtil.collect(new ArgumentMatcher(this) {
            @Override
            protected PsiElement transform(XmlTag element) {
                return myElementFactory.wrapElement(element, XsltWithParam.class);
            }
        }), XsltWithParam.class);
    }
}