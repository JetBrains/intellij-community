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
package org.intellij.lang.xpath;

import com.intellij.lang.BracePair;
import com.intellij.lang.Language;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.cacheBuilder.SimpleWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.refactoring.DefaultRefactoringSupportProvider;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.refactoring.RefactoringActionHandler;
import org.intellij.lang.xpath.completion.CompletionLists;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.psi.XPathFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class XPathLanguage extends Language {
    public static final String ID = "XPath";

    XPathLanguage() {
        super(ID);
    }

  @Override
  public XPathFileType getAssociatedFileType() {
    return XPathFileType.XPATH;
  }

  public static class XPathPairedBraceMatcher implements PairedBraceMatcher {
        private BracePair[] myBracePairs;

        public BracePair[] getPairs() {
            if (myBracePairs == null) {
                myBracePairs = new BracePair[]{
                        new BracePair(XPathTokenTypes.LPAREN, XPathTokenTypes.RPAREN, false),
                        new BracePair(XPathTokenTypes.LBRACKET, XPathTokenTypes.RBRACKET, false),
                };
            }
            return myBracePairs;
        }

        public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
            return openingBraceOffset + 1; // TODO: verify
        }

        public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
            return false;
        }
    }

    public static class XPathFindUsagesProvider implements FindUsagesProvider {
        @Nullable
        public WordsScanner getWordsScanner() {
            return new SimpleWordsScanner();
        }

        public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
            return psiElement instanceof XPathFunction;
        }

        @Nullable
        public String getHelpId(@NotNull PsiElement psiElement) {
            return null;
        }

        @NotNull
        public String getType(@NotNull PsiElement element) {
            return element instanceof XPathFunction ? "function" : "unknown";
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

    public static class XPathNamesValidator implements NamesValidator {
        private final XPathLexer xPathLexer = new XPathLexer();

        public synchronized boolean isIdentifier(String text, Project project) {
            xPathLexer.start(text);
            assert xPathLexer.getState() == 0;

            boolean b = xPathLexer.getTokenType() == XPathTokenTypes.NCNAME;
            xPathLexer.advance();

            if (xPathLexer.getTokenType() == null) {
                return b;
            } else if (xPathLexer.getTokenType() == XPathTokenTypes.COL) {
                xPathLexer.advance();
                b = xPathLexer.getTokenType() == XPathTokenTypes.NCNAME;
                xPathLexer.advance();
                return b && xPathLexer.getTokenType() == null;
            }

            return false;
        }

        public boolean isKeyword(String text, Project project) {
            return CompletionLists.AXIS_NAMES.contains(text) || CompletionLists.NODE_TYPE_FUNCS.contains(text) || CompletionLists.OPERATORS.contains(text);
        }
    }

    public static class XPathRefactoringSupportProvider extends DefaultRefactoringSupportProvider {
        @Override
        public boolean isSafeDeleteAvailable(PsiElement element) {
            if (!element.isWritable() || element.getContainingFile() == null) return false;
            final RefactoringSupportProvider realProvider = ContextProvider.getContextProvider(element).getRefactoringSupportProvider();
            return realProvider != null && realProvider.isSafeDeleteAvailable(element);
        }

        @Override
        public boolean doInplaceRenameFor(PsiElement element, PsiElement context) {
            if (!element.isWritable() || element.getContainingFile() == null) return false;
            final RefactoringSupportProvider realProvider = ContextProvider.getContextProvider(element).getRefactoringSupportProvider();
            return realProvider != null && realProvider.doInplaceRenameFor(element, context);
        }

        @Override
        public RefactoringActionHandler getIntroduceVariableHandler() {
            return new RefactoringActionHandler() {
                public void invoke(@NotNull Project project, Editor editor, PsiFile file, @Nullable DataContext dataContext) {
                    final RefactoringSupportProvider realProvider = ContextProvider.getContextProvider(file).getRefactoringSupportProvider();
                    if (realProvider == null || realProvider.getIntroduceVariableHandler() == null) {
                        showError(project);
                    } else {
                        final RefactoringActionHandler handler = realProvider.getIntroduceVariableHandler();
                        assert handler != null;
                        handler.invoke(project, editor, file, dataContext);
                    }
                }

                public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, @Nullable DataContext dataContext) {
                    assert elements.length > 0 : "No elements passed to handler";
                    final RefactoringSupportProvider realProvider = ContextProvider.getContextProvider(elements[0]).getRefactoringSupportProvider();
                    if (realProvider == null || realProvider.getIntroduceVariableHandler() == null) {
                        showError(project);
                    } else {
                        final RefactoringActionHandler handler = realProvider.getIntroduceVariableHandler();
                        assert handler != null;
                        handler.invoke(project, elements, dataContext);
                    }
                }

                private void showError(Project project) {
                    Messages.showErrorDialog(project, "Introduce Variable is not supported in this context", "XPath - Introduce Variable");
                }
            };
        }
    }

    public static class XPathSyntaxHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
        @NotNull
        protected SyntaxHighlighter createHighlighter() {
            return new XPathHighlighter();
        }
    }
}
