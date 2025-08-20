// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.rename;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.sh.ShBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class ShTextRenameRefactoring {
  private static final @NonNls String PRIMARY_VARIABLE_NAME = "PrimaryVariable";
  private static final @NonNls String OTHER_VARIABLE_NAME = "OtherVariable";

  private final Editor myEditor;
  private final Project myProject;
  private final PsiFile myPsiFile;
  private final Collection<TextRange> myOccurrenceRanges;
  private final String myOccurrenceText;
  private final TextRange myOccurrenceRangeAtCaret;
  private RangeMarker myCaretRangeMarker;
  private List<RangeHighlighter> myHighlighters;

  private ShTextRenameRefactoring(@NotNull Editor editor,
                                  @NotNull Project project,
                                  @NotNull PsiFile psiFile,
                                  @NotNull String occurrenceText,
                                  @NotNull Collection<TextRange> occurrenceRanges,
                                  @NotNull TextRange occurrenceRangeAtCaret) {
    myEditor = editor;
    myProject = project;
    myPsiFile = psiFile;
    myOccurrenceText = occurrenceText;
    myOccurrenceRanges = occurrenceRanges;
    myOccurrenceRangeAtCaret = occurrenceRangeAtCaret;
  }

  static @Nullable ShTextRenameRefactoring create(@NotNull Editor editor,
                                                  @NotNull Project project,
                                                  @NotNull String occurrenceText,
                                                  @NotNull Collection<TextRange> occurrenceRanges,
                                                  @NotNull TextRange occurrenceRangeAtCaret) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile != null) {
      return new ShTextRenameRefactoring(editor, project, psiFile, occurrenceText, occurrenceRanges, occurrenceRangeAtCaret);
    }
    return null;
  }

  void start() {
    TemplateBuilderImpl builder = new TemplateBuilderImpl(myPsiFile);
    for (TextRange occurrence : myOccurrenceRanges) {
      if (occurrence.equals(myOccurrenceRangeAtCaret)) {
        builder.replaceElement(myPsiFile, occurrence, PRIMARY_VARIABLE_NAME, new MyExpression(myOccurrenceText), true);
      }
      else {
        builder.replaceElement(myPsiFile, occurrence, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
      }
    }
    createCaretRangeMarker();
    WriteCommandAction.writeCommandAction(myProject).withName(ShBundle.message("sh.rename.occurence", myOccurrenceText)).run(() -> startTemplate(builder));
  }

  private void createCaretRangeMarker() {
    int offset = myEditor.getCaretModel().getOffset();
    myCaretRangeMarker = myEditor.getDocument().createRangeMarker(offset, offset);
    myCaretRangeMarker.setGreedyToLeft(true);
    myCaretRangeMarker.setGreedyToRight(true);
  }

  private void startTemplate(TemplateBuilderImpl builder) {
    int caretOffset = myEditor.getCaretModel().getOffset();

    TextRange range = myPsiFile.getTextRange();
    assert range != null;
    RangeMarker rangeMarker = myEditor.getDocument().createRangeMarker(range);

    Template template = builder.buildInlineTemplate();
    template.setToShortenLongNames(false);
    template.setToReformat(false);

    myEditor.getCaretModel().moveToOffset(rangeMarker.getStartOffset());

    TemplateManager.getInstance(myProject).startTemplate(myEditor, template, new MyTemplateListener());
    restoreOldCaretPositionAndSelection(caretOffset);
    myHighlighters = new ArrayList<>();
    highlightTemplateVariables(template);
  }

  private void highlightTemplateVariables(@NotNull Template template) {
    if (myHighlighters != null) { // can be null if finish is called during testing
      Map<TextRange, TextAttributes> rangesToHighlight = new HashMap<>();
      TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
      if (templateState != null) {
        EditorColorsManager colorsManager = EditorColorsManager.getInstance();
        for (int i = 0; i < templateState.getSegmentsCount(); i++) {
          TextRange segmentOffset = templateState.getSegmentRange(i);
          TextAttributes attributes = getAttributes(colorsManager, template.getSegmentName(i));
          if (attributes != null) {
            rangesToHighlight.put(segmentOffset, attributes);
          }
        }
      }
      addHighlights(rangesToHighlight, myHighlighters);
    }
  }

  private static @Nullable TextAttributes getAttributes(@NotNull EditorColorsManager colorsManager, @NotNull String segmentName) {
    if (segmentName.equals(PRIMARY_VARIABLE_NAME)) {
      return colorsManager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);
    }
    if (segmentName.equals(OTHER_VARIABLE_NAME)) {
      return colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    }
    return null;
  }

  private void addHighlights(@NotNull Map<TextRange, TextAttributes> ranges,
                             @NotNull Collection<RangeHighlighter> highlighters) {
    HighlightManager highlightManager = HighlightManager.getInstance(myProject);
    for (Map.Entry<TextRange, TextAttributes> entry : ranges.entrySet()) {
      TextRange range = entry.getKey();
      TextAttributes attributes = entry.getValue();
      highlightManager.addOccurrenceHighlight(myEditor, range.getStartOffset(), range.getEndOffset(), attributes, 0, highlighters, null);
    }

    for (RangeHighlighter highlighter : highlighters) {
      highlighter.setGreedyToLeft(true);
      highlighter.setGreedyToRight(true);
    }
  }

  private void clearHighlights() {
    if (myHighlighters != null) {
      if (!myProject.isDisposed()) {
        HighlightManager highlightManager = HighlightManager.getInstance(myProject);
        for (RangeHighlighter highlighter : myHighlighters) {
          highlightManager.removeSegmentHighlighter(myEditor, highlighter);
        }
      }
      myHighlighters = null;
    }
  }

  private void restoreOldCaretPositionAndSelection(int offset) {
    Runnable runnable = () -> myEditor.getCaretModel().moveToOffset(restoreCaretOffset(offset));

    LookupImpl lookup = (LookupImpl) LookupManager.getActiveLookup(myEditor);
    if (lookup != null && lookup.getLookupStart() <= (restoreCaretOffset(offset))) {
      lookup.setLookupFocusDegree(LookupFocusDegree.UNFOCUSED);
      lookup.performGuardedChange(runnable);
    }
    else {
      runnable.run();
    }
  }

  private int restoreCaretOffset(int offset) {
    return myCaretRangeMarker.isValid() ? myCaretRangeMarker.getEndOffset() : offset;
  }

  private static final class MyExpression extends Expression {
    private final String myInitialText;

    private MyExpression(@NotNull String initialText) {
      myInitialText = initialText;
    }

    @Override
    public Result calculateResult(ExpressionContext context) {
      Editor editor = context.getEditor();
      if (editor != null) {
        TextResult insertedValue = context.getVariableValue(PRIMARY_VARIABLE_NAME);
        if (insertedValue != null && !insertedValue.getText().isEmpty()) {
          return insertedValue;
        }
      }
      return new TextResult(myInitialText);
    }

    @Override
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      return LookupElement.EMPTY_ARRAY;
    }
  }

  private class MyTemplateListener extends TemplateEditingAdapter {
    @Override
    public void beforeTemplateFinished(@NotNull TemplateState templateState, Template template) {
      clearHighlights();
    }

    @Override
    public void templateCancelled(Template template) {
      clearHighlights();
    }
  }
}
