
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