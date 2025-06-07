// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath.xslt.psi.impl;

import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.psi.XPathFunction;
import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltParameter;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class XsltFindUsagesProvider implements FindUsagesProvider {
    @Override
    public @Nullable WordsScanner getWordsScanner() {
        return LanguageFindUsages.getWordsScanner(XMLLanguage.INSTANCE);
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
        return psiElement instanceof PsiNamedElement;
    }

    @Override
    public @Nullable String getHelpId(@NotNull PsiElement psiElement) {
        return null;
    }

    @Override
    public @NotNull String getType(@NotNull PsiElement element) {
        if (element instanceof XsltParameter) {
            return getParameterType((XsltParameter)element);
        }
        if (element instanceof XPathVariable) return XPathBundle.message("find.usages.type.variable");
        if (element instanceof XsltTemplate) return XPathBundle.message("find.usages.type.template");
        if (element instanceof XPathFunction) return XPathBundle.message("find.usages.type.function");
        if (element instanceof ImplicitModeElement) return XPathBundle.message("find.usages.type.mode");
        return XPathBundle.message("find.usages.type.unknown");
    }

    private static @Nls String getParameterType(XsltParameter myTarget) {
        final XmlTag parentTag = PsiTreeUtil.getParentOfType(myTarget.getNavigationElement(), XmlTag.class);
        if (parentTag != null) {
            if (XsltSupport.isXsltRootTag(parentTag)) {
                return XPathBundle.message("find.usages.type.stylesheet.parameter");
            }
            else if (XsltSupport.isTemplate(parentTag, false)) {
                return XPathBundle.message("find.usages.type.template.parameter");
            }
        }
        return XPathBundle.message("find.usages.type.parameter");
    }

    @Override
    public @NotNull @NlsSafe String getDescriptiveName(@NotNull PsiElement element) {
        if (element instanceof PsiNamedElement) {
            final String name = ((PsiNamedElement)element).getName();
            if (name != null) return name;
        }
        return element.toString();
    }

    @Override
    public @NotNull @NlsSafe String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        if (useFullName) {
            if (element instanceof NavigationItem navigationItem) {
              final ItemPresentation presentation = navigationItem.getPresentation();
                if (presentation != null && presentation.getPresentableText() != null) {
                    return presentation.getPresentableText();
                }
                final String name = navigationItem.getName();
                if (name != null) {
                    return name;
                }
            }
        }
        if (element instanceof PsiNamedElement) {
            final String name = ((PsiNamedElement)element).getName();
            if (name != null) return name;
        }
        return element.toString();
    }
}
