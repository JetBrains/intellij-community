
/*
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 14.12.2005
 * Time: 18:34:48
 */
package org.intellij.lang.xpath.xslt.psi.impl;

import org.intellij.lang.xpath.xslt.psi.XsltWithParam;
import org.intellij.lang.xpath.xslt.psi.XsltTemplateInvocation;
import org.intellij.lang.xpath.xslt.util.ArgumentMatcher;
import org.intellij.lang.xpath.psi.impl.ResolveUtil;

import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

abstract class XsltTemplateInvocationBase extends XsltElementImpl implements XsltTemplateInvocation {
    public XsltTemplateInvocationBase(XmlTag target) {
        super(target);
    }

    @NotNull
    public XsltWithParam[] getArguments() {
        return convertArray(ResolveUtil.collect(new ArgumentMatcher(this) {
            @Override
            protected PsiElement transform(XmlTag element) {
                return myElementFactory.wrapElement(element, XsltWithParam.class);
            }
        }), XsltWithParam.class);
    }
}