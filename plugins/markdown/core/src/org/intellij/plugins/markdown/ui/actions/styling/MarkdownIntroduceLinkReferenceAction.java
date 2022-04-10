// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions.styling;

import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.find.FindManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ReplacePromptDialog;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination;
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.intellij.plugins.markdown.lang.MarkdownElementTypes.*;

public class MarkdownIntroduceLinkReferenceAction extends AnAction implements DumbAware {
  private static final String VAR_NAME = "reference";

  @Override
  public void update(@NotNull AnActionEvent e) {

    Pair<PsiFile, Editor> fileAndEditor = getFileAndEditor(e);
    if (fileAndEditor == null) {
      return;
    }

    Caret caret = fileAndEditor.getSecond().getCaretModel().getCurrentCaret();
    final var elements = MarkdownActionUtil.getElementsUnderCaretOrSelection(fileAndEditor.getFirst(), caret);

    PsiElement parentLink =
      MarkdownActionUtil.getCommonParentOfTypes(elements.getFirst(), elements.getSecond(), MarkdownTokenTypeSets.LINKS);

    if (parentLink == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    e.getPresentation().setEnabled(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Pair<PsiFile, Editor> fileAndEditor = getFileAndEditor(e);
    if (fileAndEditor == null) {
      return;
    }

    final Editor editor = fileAndEditor.getSecond();
    final PsiFile file = fileAndEditor.getFirst();

    Caret caret = editor.getCaretModel().getCurrentCaret();
    final var elements = MarkdownActionUtil.getElementsUnderCaretOrSelection(file, caret);

    PsiElement link =
      MarkdownActionUtil.getCommonTopmostParentOfTypes(elements.getFirst(), elements.getSecond(), MarkdownTokenTypeSets.LINKS);

    if (link == null) {
      return;
    }

    Project project = link.getProject();
    WriteCommandAction.runWriteCommandAction(file.getProject(), null, null, () -> {
      //disable postprocess reformatting, cause otherwise we will lose psi pointers after [doPostponedOperationsAndUnblockDocument]
      PostprocessReformattingAspect.getInstance(file.getProject()).disablePostprocessFormattingInside(
        () -> {
          if (!file.isValid()) {
            return;
          }

          Pair<PsiElement, PsiElement> referencePair = createLinkDeclarationAndReference(project, link, "reference");

          insertLastNewLines(file, 2);
          PsiElement declaration = file.addAfter(referencePair.getSecond(), file.getLastChild());
          PsiElement reference = link.replace(referencePair.getFirst());

          String url = Objects.requireNonNull(PsiTreeUtil.getChildOfType(declaration, MarkdownLinkDestination.class)).getText();

          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());

          TemplateBuilderImpl builder = (TemplateBuilderImpl)TemplateBuilderFactory.getInstance().createTemplateBuilder(file);
          PsiElement declarationLabel = declaration.getFirstChild();
          PsiElement referenceLabel = reference.getFirstChild().getLastChild();

          Expression expression =
            ApplicationManager.getApplication().isUnitTestMode() ? new TextExpression("reference") : new EmptyExpression();
          builder
            .replaceElement(declarationLabel, TextRange.create(1, declarationLabel.getTextLength() - 1), VAR_NAME, expression, true);
          builder
            .replaceElement(referenceLabel, TextRange.create(1, referenceLabel.getTextLength() - 1), VAR_NAME, expression, true);

          editor.getCaretModel().moveToOffset(0);
          Template template = builder.buildInlineTemplate();

          PsiElement title = referencePair.getSecond().getLastChild();
          String titleText = null;
          if (PsiUtilCore.getElementType(title) == LINK_TITLE) {
            titleText = title.getText();
          }

          TemplateManager.getInstance(project).startTemplate(editor, template, new DuplicatesFinder(file, editor, url, titleText));
        });

      //reformat at the end
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    });
  }

  @Nullable
  private static Pair<PsiFile, Editor> getFileAndEditor(@NotNull AnActionEvent e) {
    final Editor editor = MarkdownActionUtil.findMarkdownTextEditor(e);
    final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (editor == null || psiFile == null || !psiFile.isValid()) {
      return null;
    }

    return Pair.create(psiFile, editor);
  }

  private static void insertLastNewLines(@NotNull PsiFile psiFile, int num) {
    assert num >= 0: "Cannot insert negative number of new lines";

    PsiElement newLines = MarkdownPsiElementFactory.createNewLines(psiFile.getProject(), num);
    psiFile.addRange(newLines.getFirstChild(), newLines.getLastChild());
  }

  public static void replaceDuplicate(@NotNull PsiElement match, @NotNull String referenceText) {
    WriteCommandAction.runWriteCommandAction(match.getProject(), null, null, () -> {
      PsiFile file = match.getContainingFile();
      if (!file.isValid()) {
        return;
      }

      match.replace(createLinkDeclarationAndReference(match.getProject(), match, referenceText).getFirst());
    });
  }

  @NotNull
  public static Pair<PsiElement, PsiElement> createLinkDeclarationAndReference(Project project, PsiElement link, String referenceText) {
    String text = null;
    String title = null;
    String url = getUrl(link);

    if (PsiUtilCore.getElementType(link) == INLINE_LINK) {
      SyntaxTraverser<PsiElement> syntaxTraverser = SyntaxTraverser.psiTraverser();

      PsiElement textElement = syntaxTraverser.children(link).find(child -> PsiUtilCore.getElementType(child) == LINK_TEXT);
      if (textElement != null) {
        text = textElement.getText();
        assert text.startsWith("[") && text.endsWith("]");
        text = text.substring(1, text.length() - 1);
      }

      PsiElement titleElement =
        syntaxTraverser.children(link).find(child -> PsiUtilCore.getElementType(child) == LINK_TITLE);
      if (titleElement != null) {
        title = titleElement.getText();
      }
    }

    assert url != null;

    if (text == null) {
      text = url;
    }

    return MarkdownPsiElementFactory.createLinkDeclarationAndReference(project, url, text, title, referenceText);
  }

  @Nullable
  public static String getUrl(@NotNull PsiElement link) {
    String url = null;
    IElementType type = PsiUtilCore.getElementType(link);
    if (type == AUTOLINK) {
      url = link.getFirstChild().getNextSibling().getText();
    }
    else if (type == MarkdownTokenTypes.GFM_AUTOLINK) {
      url = link.getText();
    }
    else if (type == MarkdownTokenTypes.EMAIL_AUTOLINK) {
      url = link.getText();
    }
    else if (type == INLINE_LINK) {
      final var syntaxTraverser = SyntaxTraverser.psiTraverser();
      final var child = syntaxTraverser.children(link).find(it -> PsiUtilCore.getElementType(it) == LINK_DESTINATION);
      if (child != null) {
        url = child.getText();
      }
    }

    return url;
  }

  @SuppressWarnings("Duplicates")
  public static void replaceDuplicates(@NotNull PsiElement file,
                                       @NotNull Editor editor,
                                       @NotNull List<SmartPsiElementPointer<PsiElement>> duplicates,
                                       @NotNull String referenceText,
                                       boolean showWarning) {

    String warningMessage = "";
    if (showWarning) {
      warningMessage = "\n\n" + MarkdownBundle.message("markdown.extract.link.extract.duplicates.warning");
    }
    final String message =
      MarkdownBundle.message("markdown.extract.link.extract.duplicates.description", ApplicationNamesInfo.getInstance().getProductName(),
                             duplicates.size()) + warningMessage;
    final boolean isUnittest = ApplicationManager.getApplication().isUnitTestMode();
    final Project project = file.getProject();
    final int exitCode =
      !isUnittest ? Messages.showYesNoDialog(project, message, MarkdownBundle.message("markdown.extract.link.refactoring.dialog.title"),
                                             Messages.getInformationIcon()) : Messages.YES;

    if (exitCode == Messages.YES) {
      boolean replaceAll = false;
      final Map<PsiElement, RangeHighlighter> highlighterMap = new HashMap<>();
      for (SmartPsiElementPointer<PsiElement> smartPsiElementPointer : duplicates) {
        PsiElement match = smartPsiElementPointer.getElement();
        if (match == null) {
          continue;
        }

        if (!match.isValid()) continue;

        if (!replaceAll) {
          highlightInEditor(project, editor, highlighterMap, match);

          int promptResult = FindManager.PromptResult.ALL;
          if (!isUnittest) {
            ReplacePromptDialog promptDialog = new ReplacePromptDialog(false, MarkdownBundle.message(
              "markdown.extract.link.extract.link.replace"), project);
            promptDialog.show();
            promptResult = promptDialog.getExitCode();
          }
          if (promptResult == FindManager.PromptResult.SKIP) {
            final HighlightManager highlightManager = HighlightManager.getInstance(project);
            final RangeHighlighter highlighter = highlighterMap.get(match);
            if (highlighter != null) highlightManager.removeSegmentHighlighter(editor, highlighter);
            continue;
          }

          if (promptResult == FindManager.PromptResult.CANCEL) break;

          if (promptResult == FindManager.PromptResult.OK) {
            replaceDuplicate(match, referenceText);
          }
          else if (promptResult == FindManager.PromptResult.ALL) {
            replaceDuplicate(match, referenceText);
            replaceAll = true;
          }
        }
        else {
          replaceDuplicate(match, referenceText);
        }
      }
    }
  }

  @SuppressWarnings("Duplicates")
  private static void highlightInEditor(@NotNull final Project project,
                                        @NotNull final Editor editor,
                                        @NotNull Map<PsiElement, RangeHighlighter> highlighterMap,
                                        @NotNull PsiElement element) {
    final List<RangeHighlighter> highlighters = new ArrayList<>();
    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    final int startOffset = element.getTextRange().getStartOffset();
    final int endOffset = element.getTextRange().getEndOffset();
    highlightManager.addRangeHighlight(editor, startOffset, endOffset, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, highlighters);
    highlighterMap.put(element, highlighters.get(0));
    final LogicalPosition logicalPosition = editor.offsetToLogicalPosition(startOffset);
    editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE);
  }

  private static final class DuplicatesFinder extends TemplateEditingAdapter {
    @NotNull private final String myUrl;
    @NotNull private final PsiFile myFile;
    @NotNull private final Editor myEditor;
    @Nullable private final String myTitleText;

    private DuplicatesFinder(@NotNull PsiFile file, @NotNull Editor editor, @NotNull String url, String titleText) {
      myUrl = url;
      myFile = file;
      myEditor = editor;
      myTitleText = titleText;
    }

    @Override
    public void currentVariableChanged(@NotNull TemplateState templateState, Template template, int oldIndex, int newIndex) {
      if (!ApplicationManager.getApplication().isUnitTestMode() && (oldIndex != 0 || newIndex != 1) && (oldIndex != -1 || newIndex != -1)) {
        return;
      }

      TextResult reference = templateState.getVariableValue(VAR_NAME);
      if (reference == null) {
        return;
      }

      processDuplicates(reference.getText());
    }

    public void processDuplicates(@NotNull String referenceText) {
      PsiElement[] duplicatedLinks =
        PsiTreeUtil.collectElements(myFile, element -> MarkdownTokenTypeSets.LINKS.contains(PsiUtilCore.getElementType(element))
                                                   && myUrl.equals(getUrl(element))
                                                   //inside inline links
                                                   && PsiTreeUtil.findFirstParent(element, true, element1 -> PsiUtilCore.getElementType(element1) == INLINE_LINK) == null
                                                   //generated link
                                                   && PsiTreeUtil.findFirstParent(element, element1 -> PsiUtilCore.getElementType(element1) == FULL_REFERENCE_LINK) == null);

      final var showWarning = !ContainerUtil.and(duplicatedLinks, link -> {
        final var children = link.getChildren();
        return (children.length == 0 && myTitleText == null) ||
               (children.length == 3
                && PsiUtilCore.getElementType(children[2]) == LINK_TITLE
                && children[2].getText().equals(myTitleText));
      });
      if (duplicatedLinks.length > 0) {
        final var duplicates = ContainerUtil.map(duplicatedLinks, link -> SmartPointerManager.createPointer(link));
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          replaceDuplicates(myFile, myEditor, duplicates, referenceText, showWarning);
          PsiDocumentManager.getInstance(myFile.getProject()).doPostponedOperationsAndUnblockDocument(myEditor.getDocument());
          Document document = myEditor.getDocument();
          document.setText(document.getText() + "\nTitles Warning: " + showWarning);
        } else {
          ApplicationManager.getApplication().invokeLater(() -> replaceDuplicates(myFile, myEditor, duplicates, referenceText, showWarning));
        }
      }
    }
  }
}