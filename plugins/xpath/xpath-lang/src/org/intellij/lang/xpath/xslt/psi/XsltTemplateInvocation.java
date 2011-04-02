
/*
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 14.12.2005
 * Time: 18:31:50
 */
package org.intellij.lang.xpath.xslt.psi;

import org.jetbrains.annotations.NotNull;

public interface XsltTemplateInvocation extends XsltElement {
    @NotNull
    XsltWithParam[] getArguments();
}