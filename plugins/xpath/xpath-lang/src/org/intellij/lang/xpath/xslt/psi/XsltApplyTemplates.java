
/*
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 14.12.2005
 * Time: 18:33:40
 */
package org.intellij.lang.xpath.xslt.psi;

import org.intellij.lang.xpath.psi.XPathExpression;

import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;

public interface XsltApplyTemplates extends XsltTemplateInvocation {
    @Nullable
    XPathExpression getSelect();

    @Nullable
    QName getMode();
}