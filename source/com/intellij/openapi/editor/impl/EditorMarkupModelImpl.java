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
  private MarkSpots myMarkSpots = new MarkSpots();
  private int myScrollBarHeight;

  private static class MarkSpot {
    private int yStart;
    private int yEnd;
    private RangeHighlighter highlighter;
    public boolean drawTopBorder;
    public boolean drawBottomBorder;

    public MarkSpot(final int yStart, final int yEnd, final RangeHighlighter highlighter) {
      this.yStart = yStart;
      this.yEnd = yEnd;
      this.highlighter = highlighter;
    }

    private boolean near(MouseEvent e, double width) {
      final int x = e.getX();
      final int y = e.getY();
      return 0 <= x && x < width && yStart - getMinHeight() <= y && y < yEnd + getMinHeight();
    }
  }

  private class MarkSpots {
    private List<MarkSpot> mySpots = null;
    private List<MarkSpot> mySpotsSortedByLayer;
    private void clear() {
      mySpots = null;
      mySpotsSortedByLayer = null;
    }

    public boolean showToolTipByMouseMove(final MouseEvent e, final double width) {
      LineTooltipRenderer bigRenderer = null;
      List<RangeHighlighter> highlighters = new SmartList<RangeHighlighter>();
      boolean wereInsideMarkSpot = false;
      for (int i = 0; i < mySpots.size(); i++) {
        MarkSpot markSpot = mySpots.get(i);
        if (!markSpot.near(e, width)) {
          if (wereInsideMarkSpot) break;
          continue;
        }
        wereInsideMarkSpot = true;
        RangeHighlighter marker = markSpot.highlighter;
        highlighters.add(marker);
      }
      // move high priority tips up
      Collections.sort(highlighters, new Comparator<RangeHighlighter>() {
        public int compare(final RangeHighlighter o1, final RangeHighlighter o2) {
          return o2.getLayer() - o1.getLayer();
        }
      });
      List<HighlightInfo> infos = new SmartList<HighlightInfo>();
      for (int i = 0; i < highlighters.size(); i++) {
        RangeHighlighter marker = highlighters.get(i);
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
          final LineTooltipRenderer renderer = new LineTooltipRenderer(composite);
          renderer.addBelow(bigRenderer.getText());
          bigRenderer = renderer;
        }
      }
      if (bigRenderer != null) {
        showTooltip(e, bigRenderer);
        return true;
      }
      return false;
    }

    private void recalcMarkSpots() {
      if (mySpots != null) return;
      final List<RangeHighlighter> sortedHighlighters = getSortedHighlighters();
      mySpots = new ArrayList<MarkSpot>();
      int prevEndPosition = 0;
      boolean prevIsThin = false;
      for (int i = 0; i < sortedHighlighters.size(); i++) {
        RangeHighlighter mark = sortedHighlighters.get(i);

        if (!mark.isValid() || mark.getErrorStripeMarkColor() == null) return;

        int visStartLine = myEditor.logicalToVisualPosition(
          new LogicalPosition(mark.getDocument().getLineNumber(mark.getStartOffset()), 0)
        ).line;

        int visEndLine = myEditor.logicalToVisualPosition(
          new LogicalPosition(mark.getDocument().getLineNumber(mark.getEndOffset()), 0)
        ).line;

        int yStartPosition = visibleLineToYPosition(visStartLine, myScrollBarHeight);
        int yEndPosition = visibleLineToYPosition(visEndLine, myScrollBarHeight);

        if (yEndPosition - yStartPosition < getMinHeight()) {
          yEndPosition = yStartPosition + getMinHeight();
        }
        final MarkSpot markSpot = new MarkSpot(yStartPosition, yEndPosition, mark);
        markSpot.drawTopBorder = mark.isThinErrorStripeMark() != prevIsThin || prevEndPosition < yStartPosition;
        mySpots.add(markSpot);
        if (i != 0 && mySpots.get(i-1).yEnd < yStartPosition) {
          mySpots.get(i-1).drawBottomBorder = true;
        }

        prevEndPosition = yEndPosition;
        prevIsThin = mark.isThinErrorStripeMark();
      }
      mySpotsSortedByLayer = new ArrayList<MarkSpot>(mySpots);
      Collections.sort(mySpotsSortedByLayer, new Comparator<MarkSpot>() {
        public int compare(final MarkSpot o1, final MarkSpot o2) {
          return o1.highlighter.getLayer() - o2.highlighter.getLayer();
        }
      });
    }

    private void repaint(Graphics g, final int width) {
      recalcMarkSpots();
      for (int i = 0; i < mySpotsSortedByLayer.size(); i++) {
        MarkSpot markSpot = mySpotsSortedByLayer.get(i);

        int y = markSpot.yStart;
        RangeHighlighter mark = markSpot.highlighter;

        int yEndPosition = markSpot.yEnd;

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
        if (markSpot.drawTopBorder) {
          g.drawLine(x + 1, y, x + paintWidth - 1, y);
        }
        g.setColor(darker);
        if (markSpot.drawBottomBorder) {
          g.drawLine(x + 1, y + height, x + paintWidth, y + height);
        }
        g.drawLine(x + paintWidth, y, x + paintWidth, y + height - 1);
      }
    }

    public void doClick(final MouseEvent e, final int width) {
      MarkSpot nearestSpot = null;
      for (int i = 0; i < mySpots.size(); i++) {
        MarkSpot markSpot = mySpots.get(i);

        if (markSpot.near(e, width)) {
          if (nearestSpot == null || Math.abs(nearestSpot.yStart - e.getY()) > Math.abs(markSpot.yStart - e.getY())) {
            nearestSpot = markSpot;
          }
        }
        else {
          if (nearestSpot != null) break;
        }
      }
      if (nearestSpot == null) return;
      RangeHighlighter marker = nearestSpot.highlighter;
      int offset = marker.getStartOffset();

      final Document doc = myEditor.getDocument();
      if (doc.getLineCount() > 0) {
        // Necessary to expand folded block even if navigating just before one
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
    }
  }

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
        Collections.sort(myCachedSortedHighlighters, new Comparator<RangeHighlighter>() {
          public int compare(final RangeHighlighter h1, final RangeHighlighter h2) {
            return h1.getStartOffset() - h2.getStartOffset();
          }
        });
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

        myMarkSpots.repaint(g,getWidth());
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
      myMarkSpots.doClick(e, getWidth());
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

      if (myMarkSpots.showToolTipByMouseMove(e,getWidth())) {
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
    myMarkSpots.clear();
  }

  private static int getMinHeight() {
    return DaemonCodeAnalyzerSettings.getInstance().getErrorStripeMarkMinHeight();
  }
}
