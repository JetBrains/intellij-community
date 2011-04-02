
/*
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 14.12.2005
 * Time: 18:47:05
 */
package org.intellij.lang.xpath.xslt.psi;

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public interface XsltNamedElement extends XsltElement {

    @Nullable
    XmlAttribute getNameAttribute();

    @Nullable
    PsiElement getNameIdentifier();
}