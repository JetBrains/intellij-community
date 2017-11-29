
package org.intellij.lang.xpath.xslt.psi;

import org.jetbrains.annotations.NotNull;

public interface XsltTemplateInvocation extends XsltElement {
    @NotNull
    XsltWithParam[] getArguments();
}