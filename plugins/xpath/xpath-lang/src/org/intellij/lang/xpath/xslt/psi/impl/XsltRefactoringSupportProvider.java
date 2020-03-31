// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.xslt.psi.impl;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.LocalSearchScope;
import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.jetbrains.annotations.NotNull;

public class XsltRefactoringSupportProvider extends RefactoringSupportProvider {
    @Override
    public boolean isInplaceRenameAvailable(@NotNull PsiElement element, PsiElement context) {
        return element instanceof XsltVariable && element.getUseScope() instanceof LocalSearchScope;
    }

    @Override
    public boolean isSafeDeleteAvailable(@NotNull PsiElement element) {
        return element instanceof XPathVariable ||
               element instanceof XsltTemplate;
    }
}
