/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.psi.impl;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringActionHandler;
import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltParameter;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.lang.xpath.xslt.refactoring.introduceVariable.XsltIntroduceVariableAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XsltLanguage extends Language {
    public static final String ID = "$XSLT";
    public static final XsltLanguage INSTANCE = new XsltLanguage();

    XsltLanguage() {
        super(ID);
        LanguageFindUsages.INSTANCE.addExplicitExtension(this, new MyFindUsagesProvider());
        LanguageRefactoringSupport.INSTANCE.addExplicitExtension(this, new RefactoringSupportProvider() {
            @Override
            public boolean doInplaceRenameFor(PsiElement element, PsiElement context) {
                return element instanceof XsltVariable && element.getUseScope() instanceof LocalSearchScope;
            }

            @Override
            public RefactoringActionHandler getIntroduceVariableHandler() {
                return new XsltIntroduceVariableAction();
            }

            @Override
            public boolean isSafeDeleteAvailable(PsiElement element) {
                return element instanceof XPathVariable ||
                        element instanceof XsltTemplate;
            }
        });
    }

    private static class MyFindUsagesProvider implements FindUsagesProvider {
        @Nullable
        public WordsScanner getWordsScanner() {
            return LanguageFindUsages.INSTANCE.forLanguage(XMLLanguage.INSTANCE).getWordsScanner();
        }

        public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
            return psiElement instanceof PsiNamedElement;
        }

        @Nullable
        public String getHelpId(@NotNull PsiElement psiElement) {
            return null;
        }

        @NotNull
        public String getType(@NotNull PsiElement element) {
            if (element instanceof XsltParameter) {
                return getParameterType((XsltParameter)element);
            }
            if (element instanceof XPathVariable) return "variable";
            if (element instanceof XsltTemplate) return "template";
            if (element instanceof ImplicitModeElement) return "mode";
            return "";
        }

        private String getParameterType(XsltParameter myTarget) {
            final XmlTag parentTag = PsiTreeUtil.getParentOfType(myTarget.getNavigationElement(), XmlTag.class);
            if (parentTag != null) {
                if (XsltSupport.isXsltRootTag(parentTag)) {
                    return "stylesheet parameter";
                } else if (XsltSupport.isTemplate(parentTag, false)) {
                    return "template parameter";
                }
            }
            return "parameter";
        }

        @NotNull
        public String getDescriptiveName(@NotNull PsiElement element) {
            if (element instanceof PsiNamedElement) {
                final String name = ((PsiNamedElement)element).getName();
                if (name != null) return name;
            }
            return element.toString();
        }

        @NotNull
        public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
            if (useFullName) {
                if (element instanceof NavigationItem) {
                    final NavigationItem navigationItem = ((NavigationItem)element);
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
}
