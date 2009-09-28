/*
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.support.jaxen;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlToken;

class PsiChildAxisIterator extends NodeIterator {
    public PsiChildAxisIterator(Object contextNode) {
        super((PsiElement)contextNode);
    }

    protected PsiElement getFirstNode(PsiElement contextNode) {
        PsiElement n = contextNode.getFirstChild();
        n = skipToXmlElement(n);
        return n;
    }

    protected PsiElement getNextNode(PsiElement contextNode) {
        PsiElement n = contextNode.getNextSibling();
        n = skipToXmlElement(n);
        return n;
    }

    private PsiElement skipToXmlElement(PsiElement n) {
        // attributes cannot appear in the child axis
        // optimize: skip XmlTokens
        while (n != null && (!isXmlElement(n) || isXmlToken(n) || isAttribute(n))) {
            n = n.getNextSibling();
        }
        return n;
    }

    private boolean isAttribute(PsiElement n) {
        return (n instanceof XmlAttribute);
    }

    private boolean isXmlElement(PsiElement n) {
        return (n instanceof XmlElement || n instanceof PsiWhiteSpace);
    }

    private boolean isXmlToken(PsiElement n) {
        return n instanceof XmlToken;
    }
}
