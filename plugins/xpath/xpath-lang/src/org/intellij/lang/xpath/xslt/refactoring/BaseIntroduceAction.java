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
package org.intellij.lang.xpath.xslt.refactoring;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

import org.intellij.lang.xpath.XPathFile;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class BaseIntroduceAction<Settings extends RefactoringOptions> extends XsltRefactoringActionBase {
    protected abstract String getCommandName();

    protected abstract Settings getSettings(XPathExpression expression, Set<XPathExpression> matchingExpressions);

    protected abstract boolean extractImpl(XPathExpression expression, Set<XPathExpression> matchingExpressions, List<XmlTag> otherMatches, Settings settings);

    public String getErrorMessage(Editor editor, PsiFile file, XmlAttribute context) {
        if (context != null) {
            if (XsltSupport.isPatternAttribute(context)) {
                return "Match patterns may not contain variable references.";
            }
            if (XsltSupport.isXPathAttribute(context) && editor.getSelectionModel().hasSelection()) {
                return "Please select a complete XPath expression to extract.";
            }
        }
        return super.getErrorMessage(editor, file, context);
    }

    protected boolean actionPerformedImpl(PsiFile file, Editor editor, XmlAttribute context, int offset) {
        if (!(file instanceof XPathFile)) return false;
        
        // pattern attribute may not reference variables
        if (XsltSupport.isPatternAttribute(context)) return false;

        final SelectionModel selectionModel = editor.getSelectionModel();
        final boolean hasSelection = selectionModel.hasSelection();
        final int start = selectionModel.getSelectionStart();
        final int end = selectionModel.getSelectionEnd();

        if (hasSelection) {
            final PsiElement xpathElement = file.findElementAt(start);
            if (xpathElement != null) {
                XPathExpression expression = PsiTreeUtil.getParentOfType(xpathElement, XPathExpression.class);
                while (expression != null) {
                    if (expression.getTextRange().getStartOffset() == start) {
                        final int diff = expression.getTextRange().getEndOffset() - end;
                        if (diff == 0) {
                            extractFromExpression(editor, expression);
                            return true;
                        } else if (diff > 0) {
                            break;
                        }
                    }
                    expression = PsiTreeUtil.getParentOfType(expression, XPathExpression.class);
                }
            }
        } else {
            final XPathExpression expression = PsiTreeUtil.getChildOfType(file, XPathExpression.class);
            if (expression != null) {
                final PsiFile containingFile = expression.getContainingFile();
                assert containingFile != null;
                final TextRange range = expression.getTextRange();
                editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
                extractFromExpression(editor, expression);
                return true;
            }
        }

        return false;
    }

    private void extractFromExpression(Editor e, final XPathExpression expression) {
        final Editor editor = (e instanceof EditorWindow) ? ((EditorWindow)e).getDelegate() : e;
        
        final HighlightManager highlightManager = HighlightManager.getInstance(expression.getProject());

        final Set<XPathExpression> matchingExpressions = RefactoringUtil.collectMatchingExpressions(expression);
        final List<XmlTag> otherMatches = new ArrayList<>(matchingExpressions.size());
        final ArrayList<RangeHighlighter> highlighters = new ArrayList<>(matchingExpressions.size() + 1);
        if (matchingExpressions.size() > 0) {
            final SelectionModel selectionModel = editor.getSelectionModel();
          highlightManager.addRangeHighlight(editor, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(),
                                             EditorColors.SEARCH_RESULT_ATTRIBUTES.getDefaultAttributes(), false, highlighters);
            for (XPathExpression expr : matchingExpressions) {
                final TextRange range = XsltCodeInsightUtil.getRangeInsideHostingFile(expr);
              highlightManager.addRangeHighlight(editor, range.getStartOffset(), range.getEndOffset(),
                                                 EditorColors.SEARCH_RESULT_ATTRIBUTES.getDefaultAttributes(), false, highlighters);

                final XmlTag tag = PsiTreeUtil.getContextOfType(expr, XmlTag.class, true);
                assert tag != null;
                otherMatches.add(tag);
            }
        }

        final Settings dlg = getSettings(expression, matchingExpressions);
        if (dlg == null || dlg.isCanceled()) return;

        if (getCommandName() != null) {
            new WriteCommandAction.Simple(e.getProject(), getCommandName()) {
                protected void run() throws Throwable {
                    if (extractImpl(expression, matchingExpressions, otherMatches, dlg)) {
                        for (RangeHighlighter highlighter : highlighters) {
                            highlighter.dispose();
                        }
                    }
                }
            }.execute();
        } else {
            extractImpl(expression, matchingExpressions, otherMatches, dlg);
        }
    }
}
