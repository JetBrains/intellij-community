/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 19, 2002
 * Time: 2:56:43 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoComposite;
import com.intellij.codeInsight.hint.*;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.ErrorStripeEvent;
import com.intellij.openapi.editor.ex.ErrorStripeListener;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.util.SmartList;
import gnu.trove.TIntArrayList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class EditorMarkupModelImpl extends MarkupModelImpl implements EditorMarkupModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorMarkupModelImpl");

  private static final TooltipGroup ERROR_STRIPE_TOOLTIP_GROUP = new TooltipGroup("ERROR_STRIPE_TOOLTIP_GROUP", 0);

  private EditorImpl myEditor;
  private MyErrorPanel myErrorPanel;
  private ErrorStripeRenderer myErrorStripeRenderer = null;
  private List<ErrorStripeListener> myErrorMarkerListeners = new ArrayList<ErrorStripeListener>();
  private ErrorStripeListener[] myCachedErrorMarkerListeners = null;
  private List<RangeHighlighter> myCachedSortedHighlighters = null;
  private ErrorMarkPileList myCachedErrorMarkPileList;
  private int myScrollBarHeight;

  EditorMarkupModelImpl(EditorImpl editor) {
    super((DocumentImpl)editor.getDocument());
    myEditor = editor;
  }

  public void setErrorStripeVisible(boolean val) {
    if (val) {
      myErrorPanel = new MyErrorPanel();
      myEditor.getPanel().add(myErrorPanel,
                              myEditor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_LEFT
                              ? BorderLayout.WEST
                              : BorderLayout.EAST);
    }
    else if (myErrorPanel != null) {
      myEditor.getPanel().remove(myErrorPanel);
      myErrorPanel = null;
    }
  }

  public Editor getEditor() {
    return myEditor;
  }

  public void setErrorStripeRenderer(ErrorStripeRenderer renderer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myErrorStripeRenderer = renderer;
    HintManager.getInstance().getTooltipController().cancelTooltips();
    if (myErrorPanel != null) {
      myErrorPanel.repaint();
    }
  }

  public ErrorStripeRenderer getErrorStripeRenderer() {
    return myErrorStripeRenderer;
  }

  public void repaint() {
    EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
    myScrollBarHeight = scrollBar.getSize().height;

    if (myErrorPanel != null) {
      myErrorPanel.repaint();
    }
  }

  private List<RangeHighlighter> getSortedHighlighters() {
    if (myCachedSortedHighlighters == null) {
      myCachedSortedHighlighters = new ArrayList<RangeHighlighter>();
      RangeHighlighter[] highlighters = getAllHighlighters();
      for (int i = 0; i < highlighters.length; i++) {
        if (highlighters[i].getErrorStripeMarkColor() != null) {
          myCachedSortedHighlighters.add(highlighters[i]);
        }
      }
      final MarkupModel docMarkup = getDocument().getMarkupModel(myEditor.myProject);
      if (docMarkup != null) {
        highlighters = docMarkup.getAllHighlighters();
        for (int i = 0; i < highlighters.length; i++) {
          if (highlighters[i].getErrorStripeMarkColor() != null) {
            myCachedSortedHighlighters.add(highlighters[i]);
          }
        }
      }

      if (myCachedSortedHighlighters.size() != 0) {
        Collections.sort(
          myCachedSortedHighlighters, new Comparator() {
            public int compare(Object o1, Object o2) {
              RangeHighlighter h1 = (RangeHighlighter)o1;
              RangeHighlighter h2 = (RangeHighlighter)o2;
              return h1.getStartOffset() - h2.getEndOffset();
            }
          }
        );
      }
    }

    return myCachedSortedHighlighters;
  }

  private class MyErrorPanel extends JPanel implements MouseMotionListener, MouseListener {
    private MyErrorPanel() {
      addMouseListener(this);
      addMouseMotionListener(this);
    }

    public Dimension getPreferredSize() {
      return new Dimension(12, 0);
    }

    protected void paintComponent(Graphics g) {
      ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();

      try {
        LafManager lafManager = LafManager.getInstance();
        if (lafManager == null || lafManager.isUnderAquaLookAndFeel()) {
          g.setColor(new Color(0xF0F0F0));
          Rectangle clipBounds = g.getClipBounds();
          g.fillRect(clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);
        } else {
          super.paintComponent(g);
        }

        EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
        if (myErrorStripeRenderer != null) {
          int top = scrollBar.getDecScrollButtonHeight();
          myErrorStripeRenderer.paint(this, g, new Rectangle(0, 0, top, getWidth()));
        }

        final ErrorMarkPileList markPileList = getErrorMarkPileList();
        markPileList.paint(g, getWidth());
      }
      finally {
        ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
      }
    }

    // mouse events
    public void mouseClicked(final MouseEvent e) {
      CommandProcessor.getInstance().executeCommand(
        myEditor.myProject, new Runnable() {
          public void run() {
            doMouseClicked(e);
          }
        },
        "Move caret", null
      );
    }

    private void doMouseClicked(MouseEvent e) {
      myEditor.getContentComponent().requestFocus();
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();
      if (lineCount == 0) {
        return;
      }
      getErrorMarkPileList().doClick(e, getWidth());
    }

    public void mouseMoved(MouseEvent e) {
      EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
      int buttonHeight = scrollBar.getDecScrollButtonHeight();
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();
      if (lineCount == 0) {
        return;
      }

      if (e.getY() < buttonHeight && myErrorStripeRenderer != null) {
        String tooltipMessage = myErrorStripeRenderer.getTooltipMessage();
        showTooltip(e, new LineTooltipRenderer(tooltipMessage));
        return;
      }

      if (getErrorMarkPileList().showToolTipByMouseMove(e,getWidth())) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return;
      }

      cancelMyToolTips();

      if (getCursor().equals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))) {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }

    private void cancelMyToolTips() {
      HintManager.getInstance().getTooltipController().cancelTooltip(ERROR_STRIPE_TOOLTIP_GROUP);
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
      cancelMyToolTips();
    }

    public void mouseDragged(MouseEvent e) {
      cancelMyToolTips();
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }
  }

  private void showTooltip(MouseEvent e, final TooltipRenderer tooltipObject) {
    final TooltipController tooltipController = HintManager.getInstance().getTooltipController();
    tooltipController.showTooltipByMouseMove(myEditor, e, tooltipObject,
                                             myEditor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_RIGHT,
                                             ERROR_STRIPE_TOOLTIP_GROUP);
  }

  private int visibleLineToYPosition(int lineNumber, int scrollBarHeight) {
    EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
    int top = scrollBar.getDecScrollButtonHeight() + 1;
    int bottom = scrollBar.getIncScrollButtonHeight();
    final int targetHeight = scrollBarHeight - top - bottom;
    final int sourceHeight = myEditor.getPreferredSize().height;

    if (sourceHeight < targetHeight) {
      return top + lineNumber * myEditor.getLineHeight();
    }
    else {
      final int lineCount = sourceHeight / myEditor.getLineHeight();
      return top + (int)(((float)lineNumber / lineCount) * targetHeight);
    }
  }

  private ErrorStripeListener[] getCachedErrorMarkerListeners() {
    if (myCachedErrorMarkerListeners == null) {
      myCachedErrorMarkerListeners = myErrorMarkerListeners.toArray(
        new ErrorStripeListener[myErrorMarkerListeners.size()]
      );
    }

    return myCachedErrorMarkerListeners;
  }

  private void fireErrorMarkerClicked(RangeHighlighter marker, MouseEvent e) {
    ErrorStripeEvent event = new ErrorStripeEvent(getEditor(), e, marker);
    ErrorStripeListener[] listeners = getCachedErrorMarkerListeners();
    for (int i = 0; i < listeners.length; i++) {
      ErrorStripeListener listener = listeners[i];
      listener.errorMarkerClicked(event);
    }
  }

  public void addErrorMarkerListener(ErrorStripeListener listener) {
    myCachedErrorMarkerListeners = null;
    myErrorMarkerListeners.add(listener);
  }

  public void removeErrorMarkerListener(ErrorStripeListener listener) {
    myCachedErrorMarkerListeners = null;
    boolean success = myErrorMarkerListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public void markDirtied() {
    myCachedSortedHighlighters = null;
    myCachedErrorMarkPileList = null;
  }

  private ErrorMarkPileList getErrorMarkPileList() {
    if (myCachedErrorMarkPileList == null) {
      myCachedErrorMarkPileList = new ErrorMarkPileList();
      List<RangeHighlighter> sortedHighlighters = getSortedHighlighters();
      for (int i = 0; i < sortedHighlighters.size(); i++) {
        RangeHighlighter highlighter = sortedHighlighters.get(i);
        myCachedErrorMarkPileList.addNextMark(highlighter);
      }
    }
    return myCachedErrorMarkPileList;
  }

  private class ErrorMarkPileList {
    private List<ErrorMarkPile> list = new ArrayList<ErrorMarkPile>();

    private void addNextMark(RangeHighlighter mark) {
      if (!mark.isValid() || mark.getErrorStripeMarkColor() == null) return;

      int visStartLine = myEditor.logicalToVisualPosition(
        new LogicalPosition(mark.getDocument().getLineNumber(mark.getStartOffset()), 0)
      ).line;

      int visEndLine = myEditor.logicalToVisualPosition(
        new LogicalPosition(mark.getDocument().getLineNumber(mark.getEndOffset()), 0)
      ).line;

      int yStartPosition = visibleLineToYPosition(visStartLine, myScrollBarHeight);
      int yEndPosition = visibleLineToYPosition(visEndLine, myScrollBarHeight);

      final ErrorMarkPile prevPile = list.size() == 0 ? null : list.get(list.size() - 1);
      int prevPileStart = prevPile == null ? 0 : prevPile.yStart;
      if (prevPile != null && yStartPosition - prevPileStart < getMinHeight()) {
        prevPile.addMark(mark, yStartPosition, yEndPosition);
      }
      else {
        final ErrorMarkPile pile = new ErrorMarkPile(yStartPosition);
        pile.addMark(mark, yStartPosition, yEndPosition);
        list.add(pile);
      }
    }


    public void paint(final Graphics g, final int width) {
      for (int i = 0; i < list.size(); i++) {
        ErrorMarkPile pile = list.get(i);
        pile.paint(g, width);
      }
    }

    public void doClick(final MouseEvent e, final double width) {
      for (int i = 0; i < list.size(); i++) {
        ErrorMarkPile pile = list.get(i);
        if (pile.doClick(e, width)) return;
      }
    }

    public boolean showToolTipByMouseMove(final MouseEvent e, double width) {
      for (int i = 0; i < list.size(); i++) {
        ErrorMarkPile pile = list.get(i);
        if (pile.showToolTipByMouseMove(e, width)) return true;
      }
      return false;
    }
  }

  private static int getMinHeight() {
    return DaemonCodeAnalyzerSettings.getInstance().getErrorStripeMarkMinHeight();
  }

  // number of error marks glued together
  private class ErrorMarkPile {
    private int yStart;
    private int yEnd;
    private List<RangeHighlighter> markers = new ArrayList<RangeHighlighter>();
    private TIntArrayList paintingEndOffsets = new TIntArrayList();

    public ErrorMarkPile(final int yStart) {
      this.yStart = yStart;
    }

    public void addMark(final RangeHighlighter mark, int newYStart, int newYEnd) {
      if (newYEnd - newYStart < getMinHeight()) {
        newYEnd = newYStart + getMinHeight();
      }
      yEnd = Math.max(yEnd, newYEnd - yStart < getMinHeight() ? yStart + getMinHeight() : newYEnd);
      if (markers.size() != 0) {
        final int prevMarkIndex = markers.size() - 1;
        final RangeHighlighter prevMark = markers.get(prevMarkIndex);
        final int prevMarkEnd = paintingEndOffsets.get(prevMarkIndex);
        if (prevMark.getLayer() < mark.getLayer()) {
          // prev mark prio is lower, shorten prev mark
          paintingEndOffsets.set(prevMarkIndex, Math.min(prevMarkEnd, newYStart));
        }
        else if (prevMarkEnd > newYEnd) {
          // just drop new mark as it falls in the middle of higher priority other mark
          return;
        }
      }
      markers.add(mark);
      paintingEndOffsets.add(newYEnd);
    }

    public void paint(final Graphics g, final int width) {
      int y = yStart;
      for (int i = 0; i < markers.size(); i++) {
        RangeHighlighter mark = markers.get(i);

        int yEndPosition = i == markers.size() - 1 ? yEnd : paintingEndOffsets.get(i);

        final int height = yEndPosition - y;
        final Color color = mark.getErrorStripeMarkColor();
        g.setColor(color);

        int x = 1;
        int paintWidth = width;
        if (mark.isThinErrorStripeMark()) {
          paintWidth /= 2;
          x += paintWidth / 2;
        }

        g.fillRect(x, y, paintWidth - 1, height);
        Color brighter = color.brighter();
        Color darker = color.darker();

        g.setColor(brighter);
        g.drawLine(x, y, x, y + height);
        if (i == 0 || markers.get(i-1).isThinErrorStripeMark() != mark.isThinErrorStripeMark()) {
          g.drawLine(x + 1, y, x + paintWidth - 1, y);
        }
        g.setColor(darker);
        if (i == markers.size()-1 || markers.get(i + 1).isThinErrorStripeMark() != mark.isThinErrorStripeMark()) {
          g.drawLine(x + 1, y + height, x + paintWidth, y + height);
        }
        g.drawLine(x + paintWidth, y, x + paintWidth, y + height - 1);

        y = yEndPosition;
      }
    }

    public boolean doClick(final MouseEvent e, final double width) {
      if (!inside(e, width)) {
        return false;
      }
      final int y = e.getY();
      RangeHighlighter marker = markers.get(0);
      int offset = marker.getStartOffset();
      for (int i = 0; i< paintingEndOffsets.size(); i++) {
        final int endY = paintingEndOffsets.get(i);
        if (y < endY) {
          marker = markers.get(i);
          offset = marker.getStartOffset();
          break;
        }
      }

      final Document doc = myEditor.getDocument();
      if (doc.getLineCount() > 0) {
        // Necessary to expand folded block even if naviagting just before one
        // Very useful when navigating to first unused import statement.
        int lineEnd = doc.getLineEndOffset(doc.getLineNumber(offset));
        myEditor.getCaretModel().moveToOffset(lineEnd);
      }

      myEditor.getCaretModel().moveToOffset(offset);
      myEditor.getSelectionModel().removeSelection();
      ScrollingModel scrollingModel = myEditor.getScrollingModel();
      scrollingModel.disableAnimation();
      scrollingModel.scrollToCaret(ScrollType.CENTER);
      scrollingModel.enableAnimation();
      fireErrorMarkerClicked(marker, e);
      return true;
    }

    private boolean inside(MouseEvent e, double width) {
      final int x = e.getX();
      final int y = e.getY();
      return 0 <= x && x < width && yStart <= y && y < yEnd;
    }

    public boolean showToolTipByMouseMove(final MouseEvent e, final double width) {
      if (!inside(e, width)) {
        return false;
      }
      LineTooltipRenderer bigRenderer = null;
      List<HighlightInfo> infos = new SmartList<HighlightInfo>();
      for (int i = 0; i < markers.size(); i++) {
        RangeHighlighter marker = markers.get(i);
        final Object tooltipObject = marker.getErrorStripeTooltip();
        if (tooltipObject == null) continue;
        if (tooltipObject instanceof HighlightInfo) {
          infos.add((HighlightInfo)tooltipObject);
        }
        else {
          final String text = tooltipObject.toString();
          if (bigRenderer == null) {
            bigRenderer = new LineTooltipRenderer(text);
          }
          else {
            bigRenderer.addBelow(text);
          }
        }
      }
      if (infos.size() != 0) {
        // show errors first
        Collections.sort(infos, new Comparator<HighlightInfo>() {
          public int compare(final HighlightInfo o1, final HighlightInfo o2) {
            return o2.getSeverity().compareTo(o1.getSeverity());
          }
        });
        final HighlightInfoComposite composite = new HighlightInfoComposite(infos);
        if (bigRenderer == null) {
          bigRenderer = new LineTooltipRenderer(composite.toolTip);
        }
        else {
          bigRenderer.addBelow(composite.toolTip);
        }
      }
      if (bigRenderer != null) {
        showTooltip(e, bigRenderer);
      }
      return true;
    }
  }
}
