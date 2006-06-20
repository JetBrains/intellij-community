package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.diff.actions.MergeActionGroup;
import com.intellij.openapi.diff.actions.MergeOperations;
import com.intellij.openapi.diff.impl.EditorSource;
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.util.GutterActionRenderer;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.HashSet;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

public abstract class DiffMarkup implements EditorSource {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.openapi.diff.impl.highlighting.EditorTextAppender");
  private static final int LAYER = HighlighterLayer.FIRST + 1;

  private final ArrayList<RangeHighlighter> myHighLighters = new ArrayList<RangeHighlighter>();
  private final HashSet<RangeHighlighter> myActionHighlighters = new HashSet<RangeHighlighter>();
  private final Project myProject;
  private final ArrayList<Disposable> myDisposables = new ArrayList<Disposable>();
  private boolean myDisposed = false;

  protected DiffMarkup(Project project) {
    myProject = project;
  }

  private MarkupModel getMarkupModel() {
    Editor editor = getEditor();
    return editor == null ? null : editor.getMarkupModel();
  }

  public void highlightText(Fragment fragment, boolean drawBorder) {
    TextDiffType type = fragment.getType();
    if (type == null) return;
    TextRange range = fragment.getRange(getSide());
    TextAttributes attributes = type.getTextAttributes(getEditor());
    if (!drawBorder && range.getLength() == 0) return;
    RangeHighlighter rangeMarker;
    if (drawBorder && range.getLength() == 0) {
      TextAttributes textAttributes = new TextAttributes(null, null, attributes.getBackgroundColor(), EffectType.BOXED, TextAttributes.TRANSPARENT);
      rangeMarker = getMarkupModel().addRangeHighlighter(range.getStartOffset(), range.getStartOffset(), LAYER, textAttributes, HighlighterTargetArea.EXACT_RANGE);
    }
    else {
      rangeMarker = getMarkupModel().addRangeHighlighter(range.getStartOffset(), range.getEndOffset(), LAYER,
                                                         attributes, HighlighterTargetArea.EXACT_RANGE);
    }
    Color stripeBarColor = attributes.getErrorStripeColor();
    if (stripeBarColor != null) rangeMarker.setErrorStripeMarkColor(stripeBarColor);
    saveHighlighter(rangeMarker);
  }

  public void addLineMarker(int line, TextAttributesKey type) {
    RangeHighlighter marker = createLineMarker(type, line);
    if (marker == null) return;
    saveHighlighter(marker);
    marker.setLineMarkerRenderer(LineRenderer.bottom());
  }

  private RangeHighlighter createLineMarker(TextAttributesKey type, int line) {
    Color color = getLineSeparatorColorForType(type);
    RangeHighlighter lastHighlighter = getLastHighlighter();
    if (lastHighlighter != null &&
        lastHighlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE &&
        SeparatorPlacement.BOTTOM == lastHighlighter.getLineSeparatorPlacement()) {
      int lastLine = getDocument().getLineNumber(lastHighlighter.getStartOffset());
      LOG.assertTrue(lastLine <= line);
      if (lastLine == line) {
        Color lastLineColor = lastHighlighter.getLineSeparatorColor();
        if (color == Color.GRAY || lastLineColor != Color.GRAY) {
          return null;
        }
        else {
          removeHighlighter(lastHighlighter);
        }
      }
    }
    RangeHighlighter marker = getMarkupModel().addLineHighlighter(line, LAYER, null);
//    saveHighlighter(marker);
    marker.setLineSeparatorColor(color);
    marker.setLineSeparatorPlacement(SeparatorPlacement.BOTTOM);
    // TODO[dyoma] type should be policy
    if (type == DiffColors.DIFF_DELETED) marker.setErrorStripeMarkColor(color);
    return marker;
  }

  private void removeHighlighter(RangeHighlighter highlighter) {
    getMarkupModel().removeHighlighter(highlighter);
    myHighLighters.remove(highlighter);
    myActionHighlighters.remove(highlighter);
  }

  private Color getLineSeparatorColorForType(TextAttributesKey type) {
    LOG.assertTrue(type == DiffColors.DIFF_DELETED || type == DiffColors.DIFF_MODIFIED || type == null);
    if (type == null || type == DiffColors.DIFF_MODIFIED) return Color.GRAY;
    return TextDiffType.DELETED.getTextBackground(getEditor());
  }

  private RangeHighlighter getLastHighlighter() {
    int size = myHighLighters.size();
    return size > 0 ? myHighLighters.get(size - 1) : null;
  }

  private void saveHighlighter(RangeHighlighter marker) {
    LOG.assertTrue(marker != null);
    myHighLighters.add(marker);
  }

  public Document getDocument() {
    return getEditor().getDocument();
  }

  public void addAction(final MergeOperations.Operation operation, int lineStartOffset) {
    RangeHighlighter highlighter = createAction(operation, lineStartOffset);
    if (highlighter != null) {
      myActionHighlighters.add(highlighter);
    }
  }

  private RangeHighlighter createAction(final MergeOperations.Operation operation, int lineStartOffset) {
    if (operation == null) return null;
    RangeHighlighter highlighter =
      getMarkupModel().addRangeHighlighter(lineStartOffset, lineStartOffset,
                                           HighlighterLayer.ADDITIONAL_SYNTAX,
                                           new TextAttributes(null, null, null, null, TextAttributes.TRANSPARENT),
                                           HighlighterTargetArea.LINES_IN_RANGE);
    final MergeActionGroup.OperationAction action = new MergeActionGroup.OperationAction(operation);
    highlighter.setGutterIconRenderer(new GutterActionRenderer(action));
    return highlighter;
  }

  public void resetHighlighters() {
    removeHighlighters(myHighLighters);
    removeHighlighters(myActionHighlighters);
  }

  private void removeHighlighters(Collection<RangeHighlighter> highlighters) {
    MarkupModel markupModel = getMarkupModel();
    if (markupModel != null) {
      for (RangeHighlighter highlighter : highlighters) {
        markupModel.removeHighlighter(highlighter);
      }
    }
    highlighters.clear();
  }

  protected Project getProject() {
    return myProject;
  }

  protected void disposeEditor() {
    resetHighlighters();
    for (Disposable disposable : myDisposables) {
      disposable.dispose();
    }
    myDisposables.clear();
  }

  public void addDisposable(Disposable disposable) {
    myDisposables.add(disposable);
  }

  public String getText() {
    return getDocument().getText();
  }

  protected final boolean isDisposed() { return myDisposed; }

  public final void dispose() {
    if (isDisposed()) return;
    doDispose();
    myDisposed = true;
  }

  protected void doDispose() {
    disposeEditor();
  }

  public void removeActions() {
    removeHighlighters(myActionHighlighters);
  }
}
