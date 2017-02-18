/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.highlighting.HighlightManagerImpl;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.psi.XPathElement;
import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.psi.impl.XPathChangeUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElement;
import org.intellij.lang.xpath.xslt.psi.XsltParameter;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.lang.xpath.xslt.psi.impl.XsltLanguage;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 24.11.10
*/
public class VariableInlineHandler extends InlineActionHandler {
  private static final String NAME = "Inline";
  private static final String TITLE = "XSLT - " + NAME;

  @Override
  public boolean isEnabledForLanguage(Language l) {
    return l == XPathFileType.XPATH.getLanguage() || l == XMLLanguage.INSTANCE || l == XsltLanguage.INSTANCE;
  }

  @Override
  public boolean canInlineElement(PsiElement element) {
    final XPathVariableReference reference = PsiTreeUtil.getParentOfType(element, XPathVariableReference.class, false);
    if (reference != null) {
      return canInline(reference.resolve());
    }

    return canInline(element);
  }

  private static boolean canInline(PsiElement element) {
    return element instanceof XsltVariable && !(element instanceof XsltParameter);
  }

  @Override
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    final XPathVariableReference reference = PsiTreeUtil.getParentOfType(element, XPathVariableReference.class, false);
    if (reference != null) {
      final XPathVariable variable = reference.resolve();
      if (variable != null && canInline(variable)) {
        invoke(variable, editor);
      }
    }
    if (canInline(element)) {
      invoke((XPathVariable)element, editor);
    }
  }

  public static void invoke(@NotNull final XPathVariable variable, Editor editor) {

    final String type = LanguageFindUsages.INSTANCE.forLanguage(variable.getLanguage()).getType(variable);
    final Project project = variable.getProject();

    final XmlTag tag = ((XsltElement)variable).getTag();
    final String expression = tag.getAttributeValue("select");
    if (expression == null) {
      CommonRefactoringUtil.showErrorHint(project, editor,
              MessageFormat
                      .format("{0} ''{1}'' has no value.", StringUtil.capitalize(type), variable.getName()),
              TITLE, null);
      return;
    }

    final Collection<PsiReference> references =
            ReferencesSearch.search(variable, new LocalSearchScope(tag.getParentTag()), false).findAll();
    if (references.size() == 0) {
      CommonRefactoringUtil.showErrorHint(project, editor,
              MessageFormat.format("{0} ''{1}'' is never used.", variable.getName()),
              TITLE, null);
      return;
    }

    boolean hasExternalRefs = false;
    if (XsltSupport.isTopLevelElement(tag)) {
      final Query<PsiReference> query = ReferencesSearch.search(variable, GlobalSearchScope.allScope(project), false);
      hasExternalRefs = !query.forEach(new Processor<PsiReference>() {
        int allRefs = 0;

        public boolean process(PsiReference psiReference) {
          if (++allRefs > references.size()) {
            return false;
          } else if (!references.contains(psiReference)) {
            return false;
          }
          return true;
        }
      });
    }

    final HighlightManager highlighter = HighlightManager.getInstance(project);
    final ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
    final PsiReference[] psiReferences = references.toArray(new PsiReference[references.size()]);
    TextRange[] ranges = ContainerUtil.map2Array(psiReferences, TextRange.class, s -> {
      final PsiElement psiElement = s.getElement();
      final XmlAttributeValue context = PsiTreeUtil.getContextOfType(psiElement, XmlAttributeValue.class, true);
      if (psiElement instanceof XPathElement && context != null) {
        return XsltCodeInsightUtil.getRangeInsideHostingFile((XPathElement)psiElement).cutOut(s.getRangeInElement());
      }
      return psiElement.getTextRange().cutOut(s.getRangeInElement());
    });
    final Editor e = editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
    for (TextRange range : ranges) {
      final TextAttributes textAttributes = EditorColors.SEARCH_RESULT_ATTRIBUTES.getDefaultAttributes();
      final Color color = getScrollmarkColor(textAttributes);
      highlighter.addOccurrenceHighlight(e, range.getStartOffset(), range.getEndOffset(), textAttributes,
              HighlightManagerImpl.HIDE_BY_ESCAPE, highlighters, color);
    }

    highlighter.addOccurrenceHighlights(e, new PsiElement[]{((XsltVariable)variable).getNameIdentifier()},
                                        EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES.getDefaultAttributes(), false, highlighters);

    if (!hasExternalRefs) {
      if (!ApplicationManager.getApplication().isUnitTestMode() &&
              Messages.showYesNoDialog(MessageFormat.format("Inline {0} ''{1}''? ({2} occurrence{3})",
              type,
              variable.getName(),
              String.valueOf(references.size()),
              references.size() > 1 ? "s" : ""),
              TITLE, Messages.getQuestionIcon()) != Messages.YES) {
        return;
      }
    } else {
      if (!ApplicationManager.getApplication().isUnitTestMode() &&
              Messages.showYesNoDialog(MessageFormat.format("Inline {0} ''{1}''? ({2} local occurrence{3})\n" +
              "\nWarning: It is being used in external files. Its declaration will not be removed.",
              type,
              variable.getName(),
              String.valueOf(references.size()),
              references.size() > 1 ? "s" : ""),
              TITLE, Messages.getWarningIcon()) != Messages.YES) {
        return;
      }
    }

    final boolean hasRefs = hasExternalRefs;
    new WriteCommandAction.Simple(project, "XSLT.Inline", tag.getContainingFile()) {
      @Override
      protected void run() throws Throwable {
        try {
          for (PsiReference psiReference : references) {
            final PsiElement element = psiReference.getElement();
            if (element instanceof XPathElement) {
              final XPathElement newExpr = XPathChangeUtil.createExpression(element, expression);
              element.replace(newExpr);
            } else {
              assert false;
            }
          }

          if (!hasRefs) {
            tag.delete();
          }
        } catch (IncorrectOperationException e) {
          Logger.getInstance(VariableInlineHandler.class.getName()).error(e);
        }
      }
    }.execute();
  }

  @Nullable
  private static Color getScrollmarkColor(TextAttributes textAttributes) {
    if (textAttributes.getErrorStripeColor() != null) {
      return textAttributes.getErrorStripeColor();
    } else if (textAttributes.getBackgroundColor() != null) {
      return textAttributes.getBackgroundColor().darker();
    } else {
      return null;
    }
  }
}