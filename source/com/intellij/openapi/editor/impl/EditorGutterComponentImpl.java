/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 6, 2002
 * Time: 8:37:03 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.LineTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntProcedure;
import gnu.trove.TObjectProcedure;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

class EditorGutterComponentImpl extends EditorGutterComponentEx implements MouseListener, MouseMotionListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorGutterComponentImpl");
  private static final int START_ICON_AREA_WIDTH = 15;
  private static final int FREE_PAINTERS_AREA_WIDTH = 3;
  private static final int GAP_BETWEEN_ICONS = 3;
  private static final TooltipGroup GUTTER_TOOLTIP_GROUP = new TooltipGroup("GUTTER_TOOLTIP_GROUP", 0);

  private EditorImpl myEditor;
  private int myLineMarkerAreaWidth = START_ICON_AREA_WIDTH + FREE_PAINTERS_AREA_WIDTH;
  private int myIconsAreaWidth = START_ICON_AREA_WIDTH;
  private int myLineNumberAreaWidth = 0;
  private FoldRegion myActiveFoldRegion;
  private boolean myPopupInvokedOnPressed;
  private int myTextAnnotationGuttersSize = 0;
  private TIntArrayList myTextAnnotationGutterSizes = new TIntArrayList();
  private ArrayList<TextAnnotationGutterProvider> myTextAnnotationGutters = new ArrayList<TextAnnotationGutterProvider>();
  private Map<TextAnnotationGutterProvider, EditorGutterAction> myProviderToListener = new HashMap<TextAnnotationGutterProvider, EditorGutterAction>();
  private static final int GAP_BETWEEN_ANNOTATIONS = 6;
  private Color myBackgroundColor = null;
  private GutterDraggableObject myGutterDraggableObject;
  private DragSource myDragSource;


  public EditorGutterComponentImpl(EditorImpl editor) {
    myEditor = editor;
    if (!GraphicsEnvironment.isHeadless()) {
      new DropTarget(this, new MyDropTargetListener());
      myDragSource = DragSource.getDefaultDragSource();
      myDragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, new MyDragGestureListener());
    }
    setOpaque(true);
  }

  protected void fireResized() {
    processComponentEvent(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));
  }

  public Dimension getPreferredSize() {
    int w = getLineNumberAreaWidth() + getLineMarkerAreaWidth() + getFoldingAreaWidth() + getAnnotationsAreaWidth();
    return new Dimension(w, myEditor.getPreferredSize().height);
  }

  protected void setUI(ComponentUI newUI) {
    super.setUI(newUI);
    reinitSettings();
  }

  public void updateUI() {
    super.updateUI();
    reinitSettings();
  }

  public void reinitSettings() {
    myBackgroundColor = null;
    repaint();
  }

  public void paint(Graphics g) {
    ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();

    try {
      Rectangle clip = g.getClipBounds();
      if (clip.height < 0) return;

      final Graphics2D g2 = (Graphics2D)g;
      final AffineTransform old = g2.getTransform();

      if (isMirrored()) {
        final AffineTransform transform = new AffineTransform(old);
        transform.scale(-1, 1);
        transform.translate(-getWidth(), 0);
        g2.setTransform(transform);
      }

      paintLineNumbers(g, clip);

      Object antialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

      try {
        paintAnnotations(g, clip);
        paintFoldingBackground(g);
        paintLineMarkers(g, clip);
        paintFoldingTree(g, clip);
      }
      finally {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
      }

      g2.setTransform(old);
    }
    finally {
      ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
    }
  }

  private void paintAnnotations(Graphics g, Rectangle clip) {
    g.setColor(getBackground());
    g.fillRect(getAnnotationsAreaOffset(), clip.y, getAnnotationsAreaWidth(), clip.height);

    int x = getAnnotationsAreaOffset();

    Color color = myEditor.getColorsScheme().getColor(EditorColors.ANNOTATIONS_COLOR);
    g.setColor(color != null ? color : Color.blue);
    g.setFont(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));

    for (int i = 0; i < myTextAnnotationGutters.size(); i++) {
      TextAnnotationGutterProvider gutterProvider = myTextAnnotationGutters.get(i);
      int lineHeight = myEditor.getLineHeight();
      int startLineNumber = clip.y / lineHeight;
      int endLineNumber = (clip.y + clip.height) / lineHeight + 1;
      int lastLine = myEditor.logicalToVisualPosition(
        new LogicalPosition(Math.max(0, myEditor.getDocument().getLineCount() - 1), 0))
        .line;
      endLineNumber = Math.min(endLineNumber, lastLine + 1);
      if (startLineNumber >= endLineNumber) {
        return;
      }

      for (int j = startLineNumber; j < endLineNumber; j++) {
        int logLine = myEditor.visualToLogicalPosition(new VisualPosition(j, 0)).line;
        String s = gutterProvider.getLineText(logLine, myEditor);
        if (s != null) {
          g.drawString(s,
                       x,
                       (j + 1) * lineHeight - myEditor.getDescent());
        }
      }

      x += myTextAnnotationGutterSizes.get(i);
    }
  }

  private void paintFoldingTree(Graphics g, Rectangle clip) {
    if (isFoldingOutlineShown()) {
      paintFoldingTree((Graphics2D)g);
    }
    else {
      g.setColor(Color.white);
      int x = getWhitespaceSeparatorOffset() - 1;
      drawDottedLine((Graphics2D)g, x, clip.y, clip.y + clip.height);
    }
  }

  private void paintLineMarkers(Graphics g, Rectangle clip) {
    if (isLineMarkersShown()) {
      g.setColor(getBackground());
      g.fillRect(getLineMarkerAreaOffset(), clip.y, getLineMarkerAreaWidth(), clip.height);
      paintGutterRenderers(g);
    }
  }

  private void paintLineNumbers(Graphics g, Rectangle clip) {
    if (isLineNumbersShown()) {
      g.setColor(getBackground());
      g.fillRect(getLineNumberAreaOffset(), clip.y, getLineNumberAreaWidth(), clip.height);
      g.setColor(Color.white);
      int x = getLineNumberAreaOffset() + getLineNumberAreaWidth() - 2;
      UIUtil.drawLine(g, x, clip.y, x, clip.y + clip.height);
      paintLineNumbers(g);
    }
  }

  public Color getBackground() {
    if (myBackgroundColor == null) {
      final Color userDefinedColor = myEditor.getColorsScheme().getColor(EditorColors.LEFT_GUTTER_BACKGROUND);
      if (userDefinedColor != null) {
        myBackgroundColor = userDefinedColor;
      }
      else {
        LafManager lafManager = LafManager.getInstance();
        if (lafManager != null && lafManager.isUnderAquaLookAndFeel()) {
          myBackgroundColor = new Color(0xF0F0F0);
        }
        else {
          myBackgroundColor = super.getBackground();
        }
      }
    }
    return myBackgroundColor;
  }

  private void paintLineNumbers(Graphics g) {
    if (!isLineNumbersShown()) {
      return;
    }
    Rectangle clip = g.getClipBounds();
    int lineHeight = myEditor.getLineHeight();
    int startLineNumber = clip.y / lineHeight;
    int endLineNumber = (clip.y + clip.height) / lineHeight + 1;
    int lastLine = myEditor.logicalToVisualPosition(
      new LogicalPosition(Math.max(0, myEditor.getDocument().getLineCount() - 1), 0))
      .line;
    endLineNumber = Math.min(endLineNumber, lastLine + 1);
    if (startLineNumber >= endLineNumber) {
      return;
    }
    Color color = myEditor.getColorsScheme().getColor(EditorColors.LINE_NUMBERS_COLOR);
    g.setColor(color != null ? color : Color.blue);
    g.setFont(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));

    Graphics2D g2 = (Graphics2D)g;
    AffineTransform old = g2.getTransform();

    if (isMirrored()) {
      AffineTransform originalTransform = new AffineTransform(old);
      originalTransform.scale(-1, 1);
      originalTransform.translate(-getLineNumberAreaWidth() + 2, 0);
      g2.setTransform(originalTransform);
    }

    for (int i = startLineNumber; i < endLineNumber; i++) {
      int logLine = myEditor.visualToLogicalPosition(new VisualPosition(i, 0)).line;
      String s = "" + (logLine + 1);
      g.drawString(s,
                   getLineNumberAreaOffset() + getLineNumberAreaWidth() -
                   myEditor.getFontMetrics(Font.PLAIN).stringWidth(s) -
                   4,
                   (i + 1) * lineHeight - myEditor.getDescent());
    }

    g2.setTransform(old);
  }

  private interface RangeHighlighterProcessor {
    void process(RangeHighlighter highlighter);
  }

  private void processRangeHighlighters(RangeHighlighterProcessor p, int startOffset, int endOffset) {
    final MarkupModelEx docMarkup = (MarkupModelEx)myEditor.getDocument().getMarkupModel(myEditor.myProject);
    final HighlighterList docList = docMarkup.getHighlighterList();
    Iterator docHighlighters = docList != null ? docList.getHighlighterIterator() : null;

    final MarkupModelEx editorMarkup = (MarkupModelEx)myEditor.getMarkupModel();
    final HighlighterList editorList = editorMarkup.getHighlighterList();
    Iterator editorHighlighters = editorList != null ? editorList.getHighlighterIterator() : null;

    RangeHighlighterImpl lastDocHighlighter = null;
    RangeHighlighterImpl lastEditorHighlighter = null;

    while (true) {
      if (lastDocHighlighter == null && docHighlighters != null && docHighlighters.hasNext()) {
        lastDocHighlighter = (RangeHighlighterImpl)docHighlighters.next();
        if (!lastDocHighlighter.isValid() || lastDocHighlighter.getAffectedAreaStartOffset() > endOffset) {
          lastDocHighlighter = null;
          continue;
        }
        if (lastDocHighlighter.getAffectedAreaEndOffset() < startOffset) {
          lastDocHighlighter = null;
          //docHighlighters = null;
          continue;
        }
      }

      if (lastEditorHighlighter == null && editorHighlighters != null && editorHighlighters.hasNext()) {
        lastEditorHighlighter = (RangeHighlighterImpl)editorHighlighters.next();
        if (!lastEditorHighlighter.isValid() || lastEditorHighlighter.getAffectedAreaStartOffset() > endOffset) {
          lastEditorHighlighter = null;
          continue;
        }
        if (lastEditorHighlighter.getAffectedAreaEndOffset() < startOffset) {
          lastEditorHighlighter = null;
          //editorHighlighters = null;
          continue;
        }
      }

      if (lastDocHighlighter == null && lastEditorHighlighter == null) return;

      final RangeHighlighterImpl lowerHighlighter;

      if (less(lastDocHighlighter, lastEditorHighlighter)) {
        lowerHighlighter = lastDocHighlighter;
        lastDocHighlighter = null;
      }
      else {
        lowerHighlighter = lastEditorHighlighter;
        lastEditorHighlighter = null;
      }

      assert lowerHighlighter != null;
      if (!lowerHighlighter.isValid()) continue;

      int startLineIndex = lowerHighlighter.getDocument().getLineNumber(startOffset);
      if (startLineIndex < 0 || startLineIndex >= myEditor.getDocument().getLineCount()) continue;

      int endLineIndex = lowerHighlighter.getDocument().getLineNumber(endOffset);
      if (endLineIndex < 0 || endLineIndex >= myEditor.getDocument().getLineCount()) continue;

      if (lowerHighlighter.getEditorFilter().avaliableIn(myEditor)) {
        p.process(lowerHighlighter);
      }
    }
  }

  private boolean less(RangeHighlighter h1, RangeHighlighter h2) {
    if (h1 == null) return false;
    if (h2 == null) return true;

    return h1.getStartOffset() < h2.getStartOffset();
  }

  public void revalidateMarkup() {
    updateSize();
  }

  public void updateSize() {
    int oldIconsWidth = myLineMarkerAreaWidth;
    int oldAnnotationsWidth = myTextAnnotationGuttersSize;
    calcIconAreaWidth();
    calcAnnotationsSize();
    if (oldIconsWidth != myLineMarkerAreaWidth || oldAnnotationsWidth != myTextAnnotationGuttersSize) {
      fireResized();
    }
    repaint();
  }

  private void calcAnnotationsSize() {
    myTextAnnotationGuttersSize = 0;
    final FontMetrics fontMetrics = myEditor.getFontMetrics(Font.PLAIN);
    final int lineCount = myEditor.getDocument().getLineCount();
    for (int j = 0; j < myTextAnnotationGutters.size(); j++) {
      TextAnnotationGutterProvider gutterProvider = myTextAnnotationGutters.get(j);
      int gutterSize = 0;
      for (int i = 0; i < lineCount; i++) {
        final String lineText = gutterProvider.getLineText(i, myEditor);
        if (lineText != null) {
          gutterSize = Math.max(gutterSize, fontMetrics.stringWidth(lineText));
        }
      }
      if (gutterSize > 0) gutterSize += GAP_BETWEEN_ANNOTATIONS;
      myTextAnnotationGutterSizes.set(j, gutterSize);
      myTextAnnotationGuttersSize += gutterSize;
    }
  }

  private TIntObjectHashMap<ArrayList<GutterIconRenderer>> myLineToGutterRenderers;

  private void calcIconAreaWidth() {
    myLineToGutterRenderers = new TIntObjectHashMap<ArrayList<GutterIconRenderer>>();

    processRangeHighlighters(new RangeHighlighterProcessor() {
      public void process(RangeHighlighter highlighter) {
        GutterIconRenderer renderer = highlighter.getGutterIconRenderer();
        if (renderer == null || !highlighter.getEditorFilter().avaliableIn(myEditor)) return;

        int startOffset = highlighter.getStartOffset();
        int line = myEditor.getDocument().getLineNumber(startOffset);

        ArrayList<GutterIconRenderer> renderers = myLineToGutterRenderers.get(line);
        if (renderers == null) {
          renderers = new ArrayList<GutterIconRenderer>();
          myLineToGutterRenderers.put(line, renderers);
        }

        renderers.add(renderer);
      }
    }, 0, myEditor.getDocument().getTextLength());

    myIconsAreaWidth = START_ICON_AREA_WIDTH;

    myLineToGutterRenderers.forEachValue(new TObjectProcedure() {
      public boolean execute(Object object) {
        ArrayList<GutterIconRenderer> renderers = (ArrayList<GutterIconRenderer>)object;
        int width = 1;
        for (int i = 0; i < renderers.size(); i++) {
          GutterIconRenderer renderer = renderers.get(i);
          width += renderer.getIcon().getIconWidth();
          if (i > 0) width += GAP_BETWEEN_ICONS;
        }
        if (myIconsAreaWidth < width) {
          myIconsAreaWidth = width;
        }
        return true;
      }
    });

    myLineMarkerAreaWidth = myIconsAreaWidth + FREE_PAINTERS_AREA_WIDTH +
                            (isFoldingOutlineShown() ? 0 : getFoldingAnchorWidth() / 2);
  }

  private void paintGutterRenderers(final Graphics g) {
    Rectangle clip = g.getClipBounds();

    int firstVisibleOffset = myEditor.logicalPositionToOffset(
      myEditor.xyToLogicalPosition(new Point(0, clip.y - myEditor.getLineHeight())));
    int lastVisibleOffset = myEditor.logicalPositionToOffset(
      myEditor.xyToLogicalPosition(new Point(0, clip.y + clip.height + myEditor.getLineHeight())));

    Graphics2D g2 = (Graphics2D)g;

    Object antialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    try {
      processRangeHighlighters(new RangeHighlighterProcessor() {
        public void process(RangeHighlighter highlighter) {
          paintLineMarkerRenderer(highlighter, g);
        }
      }, firstVisibleOffset, lastVisibleOffset);
    }
    finally {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
    }

    int firstVisibleLine = myEditor.getDocument().getLineNumber(firstVisibleOffset);
    int lastVisibleLine = myEditor.getDocument().getLineNumber(lastVisibleOffset);
    paintIcons(firstVisibleLine, lastVisibleLine, g);
  }

  private void paintIcons(final int firstVisibleLine, final int lastVisibleLine, final Graphics g) {
    myLineToGutterRenderers.forEachKey(new TIntProcedure() {
      public boolean execute(int line) {
        if (firstVisibleLine > line || lastVisibleLine < line) return true;
        int startOffset = myEditor.getDocument().getLineStartOffset(line);
        if (myEditor.getFoldingModel().isOffsetCollapsed(startOffset)) return true;
        ArrayList<GutterIconRenderer> renderers = myLineToGutterRenderers.get(line);
        paintIconRow(line, renderers, g);
        return true;
      }
    });
  }

  private void paintIconRow(int line, ArrayList<GutterIconRenderer> row, final Graphics g) {
    processIconsRow(line, row, new LineGutterIconRendererProcessor() {
      public void process(int x, int y, GutterIconRenderer renderer) {
        renderer.getIcon().paintIcon(EditorGutterComponentImpl.this, g, x, y);
      }
    });
  }

  private void paintLineMarkerRenderer(RangeHighlighter highlighter, Graphics g) {
    Rectangle rect = getLineRendererRect(highlighter);

    if (rect != null) {
      final LineMarkerRenderer lineMarkerRenderer = highlighter.getLineMarkerRenderer();
      assert lineMarkerRenderer != null;
      lineMarkerRenderer.paint(myEditor, g, rect);
    }
  }

  private Rectangle getLineRendererRect(RangeHighlighter highlighter) {
    LineMarkerRenderer renderer = highlighter.getLineMarkerRenderer();
    if (renderer == null) return null;

    int startOffset = highlighter.getStartOffset();
    int endOffset = highlighter.getEndOffset();
    if (myEditor.getFoldingModel().isOffsetCollapsed(startOffset) &&
        myEditor.getFoldingModel().isOffsetCollapsed(endOffset)) {
      return null;
    }

    int startY = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(startOffset)).y;
    int endY = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(endOffset)).y;

    int height = endY - startY;
    int w = FREE_PAINTERS_AREA_WIDTH;
    int x = getLineMarkerAreaOffset() + myIconsAreaWidth;
    return new Rectangle(x, startY, w, height);
  }

  private interface LineGutterIconRendererProcessor {
    void process(int x, int y, GutterIconRenderer renderer);
  }

  private void processIconsRow(int line, ArrayList<GutterIconRenderer> row, LineGutterIconRendererProcessor processor) {
    int middleCount = 0;
    int middleSize = 0;

    int y = myEditor.logicalPositionToXY(new LogicalPosition(line, 0)).y;

    int x = getLineMarkerAreaOffset() + 1;
    for (GutterIconRenderer r : row) {
      Icon icon = r.getIcon();
      if (r.getAlignment() == GutterIconRenderer.Alignment.LEFT) {
        processor.process(x, y + getTextAlignmentShift(icon), r);
        x += icon.getIconWidth() + GAP_BETWEEN_ICONS;
      }
      else {
        if (r.getAlignment() == GutterIconRenderer.Alignment.CENTER) {
          middleCount++;
          middleSize += icon.getIconWidth() + GAP_BETWEEN_ICONS;
        }
      }
    }

    int leftSize = x - getLineMarkerAreaOffset();

    x = getLineMarkerAreaOffset() + myIconsAreaWidth;
    for (GutterIconRenderer r : row) {
      if (r.getAlignment() == GutterIconRenderer.Alignment.RIGHT) {
        Icon icon = r.getIcon();
        x -= icon.getIconWidth();
        processor.process(x, y + getTextAlignmentShift(icon), r);
        x -= GAP_BETWEEN_ICONS;
      }
    }

    int rightSize = myIconsAreaWidth + getLineMarkerAreaOffset() - x;

    if (middleCount > 0) {
      middleSize -= GAP_BETWEEN_ICONS;
      x = getLineMarkerAreaOffset() + leftSize + (myIconsAreaWidth - leftSize - rightSize - middleSize) / 2;
      for (GutterIconRenderer r : row) {
        if (r.getAlignment() == GutterIconRenderer.Alignment.CENTER) {
          Icon icon = r.getIcon();
          processor.process(x, y + getTextAlignmentShift(icon), r);
          x += icon.getIconWidth() + GAP_BETWEEN_ICONS;
        }
      }
    }
  }

  private int getTextAlignmentShift(Icon icon) {
    return myEditor.getLineHeight() - myEditor.getDescent() - icon.getIconHeight();
  }

  public Color getFoldingColor(boolean isActive) {
    ColorKey key = isActive ? EditorColors.SELECTED_FOLDING_TREE_COLOR : EditorColors.FOLDING_TREE_COLOR;
    Color color = myEditor.getColorsScheme().getColor(key);
    return color != null ? color : Color.black;
  }

  public void registerTextAnnotation(TextAnnotationGutterProvider provider) {
    myTextAnnotationGutters.add(provider);
    myTextAnnotationGutterSizes.add(0);
    updateSize();
  }

  public void registerTextAnnotation(TextAnnotationGutterProvider provider, EditorGutterAction action) {
    myTextAnnotationGutters.add(provider);
    myProviderToListener.put(provider, action);
    myTextAnnotationGutterSizes.add(0);
    updateSize();
  }

  private VisualPosition offsetToLineStartPosition(int offset) {
    int line = myEditor.getDocument().getLineNumber(offset);
    return myEditor.logicalToVisualPosition(new LogicalPosition(line, 0));
  }

  private void paintFoldingTree(Graphics2D g) {
    Rectangle clip = g.getClipBounds();

    int anchorX = getFoldingAreaOffset();
    int width = getFoldingAnchorWidth();

    FoldRegion[] visibleFoldRegions = ((FoldingModelImpl)myEditor.getFoldingModel()).fetchVisible();

    int firstVisibleOffset = myEditor.logicalPositionToOffset(
      myEditor.xyToLogicalPosition(new Point(0, clip.y - myEditor.getLineHeight())));
    int lastVisibleOffset = myEditor.logicalPositionToOffset(
      myEditor.xyToLogicalPosition(new Point(0, clip.y + clip.height + myEditor.getLineHeight())));

    for (FoldRegion visibleFoldRegion : visibleFoldRegions) {
      if (visibleFoldRegion.getStartOffset() > lastVisibleOffset) continue;
      if (visibleFoldRegion.getEndOffset() < firstVisibleOffset) continue;
      drawAnchor(visibleFoldRegion, width, clip, g, anchorX, false, false);
    }

    if (myActiveFoldRegion != null) {
      drawAnchor(myActiveFoldRegion, width, clip, g, anchorX, true, true);
      drawAnchor(myActiveFoldRegion, width, clip, g, anchorX, true, false);
    }
  }

  private void paintFoldingBackground(Graphics g) {
    Rectangle clip = g.getClipBounds();
    int lineX = getWhitespaceSeparatorOffset();
    g.setColor(getBackground());
    g.fillRect(getFoldingAreaOffset(), clip.y, getFoldingAreaWidth(), clip.height);

    g.setColor(myEditor.getBackroundColor());
    g.fillRect(lineX, clip.y, getFoldingAreaWidth(), clip.height);

    paintFoldingBoxBacgrounds((Graphics2D)g);
  }

  private void paintFoldingBoxBacgrounds(Graphics2D g) {
    if (!isFoldingOutlineShown()) return;
    Rectangle clip = g.getClipBounds();

    drawDottedLine(g, getWhitespaceSeparatorOffset(), clip.y, clip.y + clip.height);

    int anchorX = getFoldingAreaOffset();
    int width = getFoldingAnchorWidth();

    FoldRegion[] visibleFoldRegions = ((FoldingModelImpl)myEditor.getFoldingModel()).fetchVisible();

    int firstVisibleOffset = myEditor.logicalPositionToOffset(
      myEditor.xyToLogicalPosition(new Point(0, clip.y - myEditor.getLineHeight())));
    int lastVisibleOffset = myEditor.logicalPositionToOffset(
      myEditor.xyToLogicalPosition(new Point(0, clip.y + clip.height + myEditor.getLineHeight())));

    if (myActiveFoldRegion != null) {
      drawFoldingLines(myActiveFoldRegion, clip, width, anchorX, g);
    }

    for (FoldRegion visibleFoldRegion : visibleFoldRegions) {
      if (visibleFoldRegion.getStartOffset() > lastVisibleOffset) continue;
      if (visibleFoldRegion.getEndOffset() < firstVisibleOffset) continue;
      drawAnchor(visibleFoldRegion, width, clip, g, anchorX, false, true);
    }
  }

  public int getWhitespaceSeparatorOffset() {
    return getFoldingAreaOffset() + getFoldingAnchorWidth() / 2;
  }

  public void setActiveFoldRegion(FoldRegion activeFoldRegion) {
    if (myActiveFoldRegion != activeFoldRegion) {
      myActiveFoldRegion = activeFoldRegion;
      repaint();
    }
  }

  public int getHeadCenterY(FoldRegion foldRange) {
    int width = getFoldingAnchorWidth();
    VisualPosition foldStart = offsetToLineStartPosition(foldRange.getStartOffset());
    int y = myEditor.visibleLineNumberToYPosition(foldStart.line) + myEditor.getLineHeight() - myEditor.getDescent() -
            width / 2;

    return y;
  }

  private void drawAnchor(FoldRegion foldRange, int width, Rectangle clip, Graphics2D g,
                          int anchorX, boolean active, boolean paintBackground) {
    if (foldRange.isValid()) {
      VisualPosition foldStart = offsetToLineStartPosition(foldRange.getStartOffset());
      int y = myEditor.visibleLineNumberToYPosition(foldStart.line) + myEditor.getLineHeight() - myEditor.getDescent() -
              width;
      int height = width + 2;

      if (!foldRange.isExpanded()) {
        if (y <= clip.y + clip.height && y + height >= clip.y) {
          drawSquareWithPlus(g, anchorX, y, width, active, paintBackground);
        }
      }
      else {
        VisualPosition foldEnd = offsetToLineStartPosition(foldRange.getEndOffset());
        if (foldStart.line == foldEnd.line) {
          drawSquareWithMinus(g, anchorX, y, width, active, paintBackground);
        }
        else {
          int endY = myEditor.visibleLineNumberToYPosition(foldEnd.line) + myEditor.getLineHeight() -
                     myEditor.getDescent();

          if (y <= clip.y + clip.height && y + height >= clip.y) {
            drawDirectedBox(g, anchorX, y, width, height, width - 2, active, paintBackground);
          }

          if (endY - height <= clip.y + clip.height && endY >= clip.y) {
            drawDirectedBox(g, anchorX, endY, width, -height, -width + 2, active, paintBackground);
          }
        }
      }
    }
  }

  private void drawDirectedBox(Graphics2D g,
                               int anchorX,
                               int y,
                               int width,
                               int height,
                               int baseHeight,
                               boolean active, boolean paintBackground) {
    Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    if (SystemInfo.isMac && SystemInfo.JAVA_VERSION.startsWith("1.4.1")) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    try {
      int[] xPoints = new int[]{anchorX, anchorX + width, anchorX + width, anchorX + width / 2, anchorX};
      int[] yPoints = new int[]{y, y, y + baseHeight, y + height, y + baseHeight};

      if (paintBackground) {
        g.setColor(myEditor.getBackroundColor());

        g.fillPolygon(xPoints, yPoints, 5);
      }
      else {
        g.setColor(getFoldingColor(active));
        g.drawPolygon(xPoints, yPoints, 5);

        //Minus
        int minusHeight = y + baseHeight / 2 + (height - baseHeight) / 4;
        UIUtil.drawLine(g, anchorX + 2, minusHeight, anchorX + width - 2, minusHeight);
      }
    }
    finally {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
    }
  }

  private void drawSquareWithPlus(Graphics2D g,
                                  int anchorX,
                                  int y,
                                  int width,
                                  boolean active,
                                  boolean paintBackground) {
    drawSquareWithMinus(g, anchorX, y, width, active, paintBackground);

    UIUtil.drawLine(g, anchorX + width / 2, y + 2, anchorX + width / 2, y + width - 2);
  }

  private void drawSquareWithMinus(Graphics2D g,
                                   int anchorX,
                                   int y,
                                   int width,
                                   boolean active,
                                   boolean paintBackground) {
    if (paintBackground) {
      g.setColor(myEditor.getBackroundColor());
      g.fillRect(anchorX, y, width, width);
    }
    else {
      g.setColor(getFoldingColor(active));
      g.drawRect(anchorX, y, width, width);

      // Draw plus
      if (!active) g.setColor(getFoldingColor(true));
      UIUtil.drawLine(g, anchorX + 2, y + width / 2, anchorX + width - 2, y + width / 2);
    }
  }

  private void drawFoldingLines(FoldRegion foldRange, Rectangle clip, int width, int anchorX, Graphics2D g) {
    if (foldRange.isExpanded() && foldRange.isValid()) {
      VisualPosition foldStart = offsetToLineStartPosition(foldRange.getStartOffset());
      VisualPosition foldEnd = offsetToLineStartPosition(foldRange.getEndOffset());
      int startY = myEditor.visibleLineNumberToYPosition(foldStart.line + 1) - myEditor.getDescent();
      int endY = myEditor.visibleLineNumberToYPosition(foldEnd.line) + myEditor.getLineHeight() -
                 myEditor.getDescent();

      if (startY > clip.y + clip.height || endY + 1 + myEditor.getDescent() < clip.y) return;

      int lineX = anchorX + width / 2;

      g.setColor(getFoldingColor(true));
      UIUtil.drawLine(g, lineX, startY, lineX, endY);
    }
  }

  private void drawDottedLine(Graphics2D g, int lineX, int startY, int endY) {
    g.setColor(myEditor.getBackroundColor());
    UIUtil.drawLine(g, lineX, startY, lineX, endY);

    g.setColor(getFoldingColor(false));

    for (int i = startY / 2 * 2; i < endY; i += 2) {
      g.drawRect(lineX, i, 0, 0);
    }
  }

  private int getFoldingAnchorWidth() {
    return Math.min(4, myEditor.getLineHeight() / 2 - 2) * 2;
  }

  public int getFoldingAreaOffset() {
    return getLineMarkerAreaOffset() +
           getLineMarkerAreaWidth();
  }

  public int getFoldingAreaWidth() {
    return isFoldingOutlineShown()
           ? getFoldingAnchorWidth() + 2
           : isLineNumbersShown() ? getFoldingAnchorWidth() / 2 : 0;
  }

  public boolean isLineMarkersShown() {
    return myEditor.getSettings().isLineMarkerAreaShown();
  }

  public boolean isLineNumbersShown() {
    return myEditor.getSettings().isLineNumbersShown();
  }

  public boolean isFoldingOutlineShown() {
    return myEditor.getSettings().isFoldingOutlineShown() &&
           ((FoldingModelEx)myEditor.getFoldingModel()).isFoldingEnabled();
  }

  public int getLineNumberAreaWidth() {
    if (isLineNumbersShown()) {
      return myLineNumberAreaWidth;
    }
    else {
      return 0;
    }
  }

  public int getLineMarkerAreaWidth() {
    return isLineMarkersShown() ? myLineMarkerAreaWidth : 0;
  }

  public void setLineNumberAreaWidth(int lineNumberAriaWidth) {
    if (myLineNumberAreaWidth != lineNumberAriaWidth) {
      myLineNumberAreaWidth = lineNumberAriaWidth;
      fireResized();
    }
  }

  public int getLineNumberAreaOffset() {
    return 0;
  }

  public int getAnnotationsAreaOffset() {
    return getLineNumberAreaOffset() + getLineNumberAreaWidth();
  }

  public int getAnnotationsAreaWidth() {
    return myTextAnnotationGuttersSize;
  }

  public int getLineMarkerAreaOffset() {
    return getAnnotationsAreaOffset() + getAnnotationsAreaWidth();
  }

  private boolean isMirrored() {
    return myEditor.getVerticalScrollbarOrientation() != EditorEx.VERTICAL_SCROLLBAR_RIGHT;
  }

  public FoldRegion findFoldingAnchorAt(int x, int y) {
    if (!myEditor.getSettings().isFoldingOutlineShown()) return null;

    int anchorX = getFoldingAreaOffset();
    int anchorWidth = getFoldingAnchorWidth();

    FoldRegion[] visibleRanges = ((FoldingModelImpl)myEditor.getFoldingModel()).fetchVisible();
    for (FoldRegion foldRange : visibleRanges) {
      if (rectByFoldOffset(foldRange.getStartOffset(), anchorWidth, anchorX).contains(x, y)) return foldRange;
      if (rectByFoldOffset(foldRange.getEndOffset(), anchorWidth, anchorX).contains(x, y)) return foldRange;
    }

    return null;
  }

  private Rectangle rectByFoldOffset(int offset, int anchorWidth, int anchorX) {
    VisualPosition foldStart = offsetToLineStartPosition(offset);
    int anchorY = myEditor.visibleLineNumberToYPosition(foldStart.line) + myEditor.getLineHeight() -
                  myEditor.getDescent() - anchorWidth;
    Rectangle rect = new Rectangle(anchorX, anchorY, anchorWidth, anchorWidth);
    return rect;
  }

  public void mouseDragged(MouseEvent e) {
    HintManager.getInstance().getTooltipController().cancelTooltips();
  }

  public void mouseMoved(final MouseEvent e) {
    String tooltip = null;
    GutterIconRenderer renderer = getGutterRenderer(e);
    if (renderer != null) {
      tooltip = renderer.getTooltipText();
      if (renderer.isNavigateAction()) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
    }
    else {
      ActiveGutterRenderer lineRenderer = getActiveRendererByMouseEvent(e);
      if (lineRenderer != null) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }

      else {
        TextAnnotationGutterProvider provider = getProviderAtPoint(e.getPoint());
        if (provider != null && myProviderToListener.containsKey(provider)) {
          final EditorGutterAction action = myProviderToListener.get(provider);
          if (action != null) {
            setCursor(action.getCursor(getLineNumAtPoint(e.getPoint())));
          }
        }
      }
    }

    TooltipController controller = HintManager.getInstance().getTooltipController();
    if (tooltip != null && tooltip.length() != 0) {
      controller.showTooltipByMouseMove(myEditor, e, new LineTooltipRenderer(tooltip), false, GUTTER_TOOLTIP_GROUP);
    }
    else {
      controller.cancelTooltip(GUTTER_TOOLTIP_GROUP);
    }
  }

  public void mouseClicked(MouseEvent e) {
    if (e.isPopupTrigger()) {
      invokePopup(e);
    }
  }

  private void fireEventToTextAnnotationListeners(final MouseEvent e) {
    if (myEditor.getMouseEventArea(e) == EditorMouseEventArea.ANNOTATIONS_AREA) {
      final Point clickPoint = e.getPoint();

      final TextAnnotationGutterProvider provider = getProviderAtPoint(clickPoint);

      if (provider == null) {
        return;
      }

      if (myProviderToListener.containsKey(provider)) {
        int line = getLineNumAtPoint(clickPoint);

        if (line > 0) {
          myProviderToListener.get(provider).doAction(line);
        }

      }
    }
  }

  private int getLineNumAtPoint(final Point clickPoint) {
    return myEditor.xyToLogicalPosition(new Point(0, clickPoint.y)).line;
  }

  private TextAnnotationGutterProvider getProviderAtPoint(final Point clickPoint) {
    int current = getAnnotationsAreaOffset();
    if (clickPoint.x < current) return null;
    for (int i = 0; i < myTextAnnotationGutterSizes.size(); i++) {
      current += myTextAnnotationGutterSizes.get(i);
      if (clickPoint.x <= current) return myTextAnnotationGutters.get(i);
    }

    return null;
  }

  public void mousePressed(MouseEvent e) {
    if (e.isPopupTrigger()) {
      invokePopup(e);
      myPopupInvokedOnPressed = true;
    }
  }

  public void mouseReleased(final MouseEvent e) {
    if (e.isPopupTrigger()) {
      invokePopup(e);
      return;
    }

    if (myPopupInvokedOnPressed) {
      myPopupInvokedOnPressed = false;
      return;
    }

    GutterIconRenderer renderer = getGutterRenderer(e);
    AnAction clickAction = null;
    if (renderer != null) {
      clickAction = (MouseEvent.BUTTON2_MASK & e.getModifiers()) > 0
                    ? renderer.getMiddleButtonClickAction()
                    : renderer.getClickAction();
    }
    if (clickAction != null) {
      clickAction.actionPerformed(new AnActionEvent(e, myEditor.getDataContext(), "ICON_NAVIGATION", clickAction.getTemplatePresentation(),
                                                    ActionManager.getInstance(),
                                                    e.getModifiers()));
      e.consume();
      repaint();
    }
    else {
      ActiveGutterRenderer lineRenderer = getActiveRendererByMouseEvent(e);
      if (lineRenderer != null) {
        lineRenderer.doAction(myEditor, e);
      } else {
        fireEventToTextAnnotationListeners(e);
      }
    }
  }

  private ActiveGutterRenderer getActiveRendererByMouseEvent(final MouseEvent e) {
    final ActiveGutterRenderer[] gutterRenderer = new ActiveGutterRenderer[]{null};
    if (findFoldingAnchorAt(e.getX(), e.getY()) == null) {
      if (!e.isConsumed() &&
          e.getX() > getLineMarkerAreaOffset() + myIconsAreaWidth &&
          e.getX() <= getWhitespaceSeparatorOffset()) {
        Rectangle clip = myEditor.getScrollingModel().getVisibleArea();
        int firstVisibleOffset = myEditor.logicalPositionToOffset(
          myEditor.xyToLogicalPosition(new Point(0, clip.y - myEditor.getLineHeight())));
        int lastVisibleOffset = myEditor.logicalPositionToOffset(
          myEditor.xyToLogicalPosition(new Point(0, clip.y + clip.height + myEditor.getLineHeight())));

        processRangeHighlighters(new RangeHighlighterProcessor() {
          public void process(RangeHighlighter highlighter) {
            if (gutterRenderer[0] != null) return;
            Rectangle rect = getLineRendererRect(highlighter);
            if (rect == null) return;

            int startY = rect.y;
            int endY = startY + rect.height;
            if (startY == endY) {
              startY -= 4;
              endY += 4;
            }

            if (startY < e.getY() && e.getY() <= endY) {
              if (highlighter.getLineMarkerRenderer() instanceof ActiveGutterRenderer) {
                gutterRenderer[0] = (ActiveGutterRenderer)highlighter.getLineMarkerRenderer();
              }
            }
          }
        }, firstVisibleOffset, lastVisibleOffset);
      }
    }
    return gutterRenderer[0];
  }

  public void closeAllAnnotations() {
    for (TextAnnotationGutterProvider provider : myTextAnnotationGutters) {
      provider.gutterClosed();
    }

    myTextAnnotationGutters = new ArrayList<TextAnnotationGutterProvider>();
    myTextAnnotationGutterSizes = new TIntArrayList();
    updateSize();
  }

  private class CloseAnnotationsAction extends AnAction {
    public CloseAnnotationsAction() {
      super(EditorBundle.message("close.editor.annotations.action.name"));
    }

    public void actionPerformed(AnActionEvent e) {
      closeAllAnnotations();
    }
  }

  public void invokePopup(MouseEvent e) {
    if (myEditor.getMouseEventArea(e) == EditorMouseEventArea.ANNOTATIONS_AREA) {
      DefaultActionGroup actionGroup = new DefaultActionGroup(EditorBundle.message("editor.annotations.action.group.name"), true);
      actionGroup.add(new CloseAnnotationsAction());
      JPopupMenu menu = ActionManager.getInstance().createActionPopupMenu("", actionGroup).getComponent();
      menu.show(this, e.getX(), e.getY());
    }
    else {
      GutterIconRenderer renderer = getGutterRenderer(e);
      if (renderer != null) {
        ActionGroup actionGroup = renderer.getPopupMenuActions();
        if (actionGroup != null) {
          ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN,
                                                                                        actionGroup);
          popupMenu.getComponent().show(this, e.getX(), e.getY());
          e.consume();
        }
      }
    }
  }

  public void mouseEntered(MouseEvent e) {
  }

  public void mouseExited(MouseEvent e) {
    HintManager.getInstance().getTooltipController().cancelTooltip(GUTTER_TOOLTIP_GROUP);
  }

  private GutterIconRenderer getGutterRenderer(final Point p) {
    final int ex = convertX((int)p.getX());
    int line = myEditor.xyToLogicalPosition(new Point(0, (int)p.getY())).line;
    ArrayList<GutterIconRenderer> renderers = myLineToGutterRenderers.get(line);
    if (renderers == null) return null;

    if (line >= myEditor.getDocument().getLineCount()) return null;

    int startOffset = myEditor.getDocument().getLineStartOffset(line);
    if (myEditor.getFoldingModel().isOffsetCollapsed(startOffset)) return null;

    final GutterIconRenderer[] result = new GutterIconRenderer[]{null};
    processIconsRow(line, renderers, new LineGutterIconRendererProcessor() {
      public void process(int x, int y, GutterIconRenderer renderer) {
        Icon icon = renderer.getIcon();
        if (x <= ex && ex <= x + icon.getIconWidth() &&
            y <= p.getY() && p.getY() <= y + icon.getIconHeight()) {
          result[0] = renderer;
        }
      }
    });

    return result[0];
  }

  private GutterIconRenderer getGutterRenderer(final MouseEvent e) {
    return getGutterRenderer(e.getPoint());
  }

  public int convertX(int x) {
    if (!isMirrored()) return x;
    return getWidth() - x;
  }

  public void dispose() {
    for (TextAnnotationGutterProvider gutterProvider : myTextAnnotationGutters) {
      gutterProvider.gutterClosed();
    }
    myProviderToListener.clear();
  }

  private static final DataFlavor[] FLAVORS;
  static {
    DataFlavor[] flavors;
    try {
      final Class<EditorGutterComponentImpl> aClass = EditorGutterComponentImpl.class;
      //noinspection HardCodedStringLiteral
      flavors = new DataFlavor[]{new DataFlavor(
        DataFlavor.javaJVMLocalObjectMimeType + ";class=" + aClass.getName(), "GutterTransferable", aClass.getClassLoader()
      )};
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);  // should not happen
      flavors = new DataFlavor[0];
    }
    FLAVORS = flavors;
  }

  private class MyDragGestureListener implements DragGestureListener {
    public void dragGestureRecognized(DragGestureEvent dge) {
      if ((dge.getDragAction() & DnDConstants.ACTION_MOVE) == 0) return;
      final GutterIconRenderer renderer = getGutterRenderer(dge.getDragOrigin());
      if (renderer != null) {
        final GutterDraggableObject draggableObject = renderer.getDraggableObject();
        if (draggableObject != null) {
          try {
            myGutterDraggableObject = draggableObject;
            final MyDragSourceListener dragSourceListener = new MyDragSourceListener();
            dge.startDrag(DragSource.DefaultMoveNoDrop, new Transferable () {
              public DataFlavor[] getTransferDataFlavors() {
                return FLAVORS;
              }

              public boolean isDataFlavorSupported(DataFlavor flavor) {
                DataFlavor[] flavors = getTransferDataFlavors();
                for (DataFlavor flavor1 : flavors) {
                  if (flavor.equals(flavor1)) {
                    return true;
                  }
                }
                return false;
              }

              public Object getTransferData(DataFlavor flavor) {
                return null;
              }
            }, dragSourceListener);
          }
          catch (InvalidDnDOperationException idoe) {
            // OK, can't dnd
          }
        }

      }
    }
  }

  private class MyDragSourceListener implements DragSourceListener{
    public void dragEnter(DragSourceDragEvent dsde) {
      updateCursor(dsde);
    }

    public void dragOver(DragSourceDragEvent dsde) {
      updateCursor(dsde);
    }

    public void dropActionChanged(DragSourceDragEvent dsde) {
      dsde.getDragSourceContext().setCursor(null);//setCursor (dsde.getDragSourceContext());
    }

    private void updateCursor(final DragSourceDragEvent dsde) {
      final DragSourceContext context = dsde.getDragSourceContext();
      final Point screenPoint = dsde.getLocation();
      if (screenPoint != null) {
        final Point gutterPoint = new Point(screenPoint);
        SwingUtilities.convertPointFromScreen(gutterPoint, EditorGutterComponentImpl.this);
        if (EditorGutterComponentImpl.this.contains(gutterPoint)){
          final Point editorPoint = new Point(screenPoint);
          SwingUtilities.convertPointFromScreen(editorPoint, myEditor.getContentComponent());
          int line = myEditor.xyToLogicalPosition(new Point(0, (int)editorPoint.getY())).line;
          final Cursor cursor = myGutterDraggableObject.getCursor(line);
          context.setCursor(cursor);
          return;
        }
      }
      context.setCursor(null);
    }

    public void dragDropEnd(DragSourceDropEvent dsde) {
      if(!dsde.getDropSuccess()) return;

       if(dsde.getDropAction() == DnDConstants.ACTION_MOVE) {
         myGutterDraggableObject.removeSelf();
       }
    }

    public void dragExit(DragSourceEvent dse) {}
  }

  private class MyDropTargetListener implements DropTargetListener {
    public void dragEnter(DropTargetDragEvent dtde) {}

    public void dragOver(DropTargetDragEvent dtde) {}

    public void dropActionChanged(DropTargetDragEvent dtde) {}

    public void drop(DropTargetDropEvent dtde) {
      if (myGutterDraggableObject != null) {
        int dropAction = dtde.getDropAction();
        if ((dropAction & DnDConstants.ACTION_MOVE) != 0) {
          int line = myEditor.xyToLogicalPosition(new Point(0, (int)dtde.getLocation().getY())).line;
          dtde.dropComplete(myGutterDraggableObject.copy(line));
          return;
        }
      }

      dtde.rejectDrop();
    }

    public void dragExit(DropTargetEvent dte) {}
  }
}
