package org.intellij.lang.xpath.xslt.context;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.xpath.XPathFile;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.ContextProviderExtension;
import org.intellij.lang.xpath.xslt.XsltSupport;

public class XsltContextProviderExtension extends ContextProviderExtension {
    public boolean accepts(XPathFile file) {
        final PsiElement context = file.getContext();
        if (!(context instanceof XmlElement)) return false;
        final XmlAttribute att = PsiTreeUtil.getParentOfType(context, XmlAttribute.class);
        if (att == null) return false;
        return XsltSupport.isXPathAttribute(att);
    }

    @NotNull
    public ContextProvider getContextProvider(XPathFile file) {
        final XmlElement xmlElement = (XmlElement)file.getContext();
        assert xmlElement != null;
        return new XsltContextProvider(xmlElement);
    }
}