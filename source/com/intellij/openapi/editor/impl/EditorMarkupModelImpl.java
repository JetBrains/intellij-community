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
import com.intellij.util.containers.collections50.PriorityQueue;
import com.intellij.util.containers.collections50.Queue;
import gnu.trove.THashSet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.*;
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

  private int offsetToLine(final int offset) {
    int visStartLine = myEditor.logicalToVisualPosition(
        new LogicalPosition(myEditor.getDocument().getLineNumber(offset), 0)
      ).line;
    return visStartLine;
  }
  private static class MarkSpot {
    private final int yStart;
    private int yEnd;
    // sorted by layers from bottom to top
    private List<RangeHighlighter> highlighters;

    public MarkSpot(final int yStart, final int yEnd) {
      this.yStart = yStart;
      this.yEnd = yEnd;
      highlighters = new SmartList<RangeHighlighter>();
    }

    private boolean near(MouseEvent e, double width) {
      final int x = e.getX();
      final int y = e.getY();
      return 0 <= x && x < width && yStart - getMinHeight() <= y && y < yEnd + getMinHeight();
    }
  }

  private class MarkSpots {
    private List<MarkSpot> mySpots;
    private void clear() {
      mySpots = null;
    }

    public boolean showToolTipByMouseMove(final MouseEvent e, final double width) {
      recalcMarkSpots();
      LineTooltipRenderer bigRenderer = null;
      final List<MarkSpot> nearestMarkSpots = getNearestMarkSpots(e, width);
      Set<RangeHighlighter> highlighters = new THashSet<RangeHighlighter>();
      for (int i = 0; i < nearestMarkSpots.size(); i++) {
        MarkSpot markSpot = nearestMarkSpots.get(i);
        highlighters.addAll(markSpot.highlighters);
      }
      List<HighlightInfo> infos = new SmartList<HighlightInfo>();
      for (Iterator<RangeHighlighter> iterator = highlighters.iterator(); iterator.hasNext();) {
        RangeHighlighter marker = iterator.next();
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
    private class PositionedRangeHighlighter {
      private final RangeHighlighter highlighter;
      private final int yStart;
      private final int yEnd;

      public PositionedRangeHighlighter(final RangeHighlighter highlighter, final int yStart, final int yEnd) {
        this.highlighter = highlighter;
        this.yStart = yStart;
        this.yEnd = yEnd;
      }

      public String toString() {
        return "PR["+yStart+"-"+yEnd+")";
      }
    }
    private PositionedRangeHighlighter getPositionedRangeHighlighter(RangeHighlighter mark) {
      int visStartLine = offsetToLine(mark.getStartOffset());
      int visEndLine = offsetToLine(mark.getEndOffset());
      int yStartPosition = visibleLineToYPosition(visStartLine);
      int yEndPosition = visibleLineToYPosition(visEndLine);
      if (yEndPosition - yStartPosition < getMinHeight()) {
        yEndPosition = yStartPosition + getMinHeight();
      }
      return new PositionedRangeHighlighter(mark, yStartPosition, yEndPosition);
    }


    private void recalcMarkSpots() {
      if (mySpots != null) return;
      final List<RangeHighlighter> sortedHighlighters = getSortedHighlighters();
      mySpots = new ArrayList<MarkSpot>();
      if (sortedHighlighters.size() == 0) return;
      Queue<PositionedRangeHighlighter> startQueue = new PriorityQueue<PositionedRangeHighlighter>(5, new Comparator<PositionedRangeHighlighter>() {
        public int compare(final PositionedRangeHighlighter o1, final PositionedRangeHighlighter o2) {
          return o1.yStart - o2.yStart;
        }
      });
      Queue<PositionedRangeHighlighter> endQueue = new PriorityQueue<PositionedRangeHighlighter>(5, new Comparator<PositionedRangeHighlighter>() {
        public int compare(final PositionedRangeHighlighter o1, final PositionedRangeHighlighter o2) {
          return o1.yEnd - o2.yEnd;
        }
      });
      int index = 0;
      MarkSpot currentSpot = null;
      while (!startQueue.isEmpty() || !endQueue.isEmpty() || index != sortedHighlighters.size()) {
        LOG.assertTrue(startQueue.size() == endQueue.size());
        final THashSet<PositionedRangeHighlighter> set = new THashSet<PositionedRangeHighlighter>(startQueue);
        LOG.assertTrue(set.containsAll(endQueue));

        final PositionedRangeHighlighter positionedMark;
        boolean addingNew;
        if (index != sortedHighlighters.size()) {
          RangeHighlighter mark = sortedHighlighters.get(index);
          if (!mark.isValid() || mark.getErrorStripeMarkColor() == null) continue;
          PositionedRangeHighlighter positioned = getPositionedRangeHighlighter(mark);
          if (!endQueue.isEmpty() && endQueue.peek().yEnd <= positioned.yStart) {
            positionedMark = endQueue.peek();
            addingNew = false;
          }
          else {
            positionedMark = positioned;
            addingNew = true;
          }
        }
        else if (!endQueue.isEmpty()) {
          positionedMark = endQueue.peek();
          addingNew = false;
        }
        else {
          LOG.error("cant be");
          return;
        }

        if (addingNew) {
          if (currentSpot == null) {
            currentSpot = new MarkSpot(positionedMark.yStart, -1);
          }
          else {
            currentSpot.yEnd = positionedMark.yStart;
            if (currentSpot.yEnd != currentSpot.yStart) {
              spitOutMarkSpot(currentSpot, startQueue);
            }
            currentSpot = new MarkSpot(positionedMark.yStart, -1);
          }
          while (index != sortedHighlighters.size()) {
             //&& sortedHighlighters.get(index).getStartOffset() == mark.getStartOffset()) {
            PositionedRangeHighlighter positioned = getPositionedRangeHighlighter(sortedHighlighters.get(index));
            if (positioned.yStart != positionedMark.yStart) break;
            startQueue.add(positioned);
            endQueue.add(positioned);
            index++;
          }
        }
        else {
          currentSpot.yEnd = positionedMark.yEnd;
          spitOutMarkSpot(currentSpot, startQueue);
          currentSpot = new MarkSpot(positionedMark.yEnd, -1);
          while (!endQueue.isEmpty() && endQueue.peek().yEnd == positionedMark.yEnd) {
            final PositionedRangeHighlighter highlighter = endQueue.remove();
            for (Iterator<PositionedRangeHighlighter> iterator = startQueue.iterator(); iterator.hasNext();) {
              PositionedRangeHighlighter positioned = iterator.next();
              if (positioned == highlighter) {
                iterator.remove();
                break;
              }
            }
            //startQueue.remove(highlighter);
          }
          if (startQueue.size() == 0)  {
            currentSpot = null;
          }
        }
      }
    }

    private void spitOutMarkSpot(final MarkSpot currentSpot, final Queue<PositionedRangeHighlighter> startQueue) {
      mySpots.add(currentSpot);
      currentSpot.highlighters = new SmartList<RangeHighlighter>();
      for (Iterator<PositionedRangeHighlighter> iterator = startQueue.iterator(); iterator.hasNext();) {
        PositionedRangeHighlighter positioned = iterator.next();
        currentSpot.highlighters.add(positioned.highlighter);
      }
      Collections.sort(currentSpot.highlighters, new Comparator<RangeHighlighter>() {
        public int compare(final RangeHighlighter o1, final RangeHighlighter o2) {
          return o1.getLayer() - o2.getLayer();
        }
      });
    }

    private void repaint(Graphics g, final int width) {
      recalcMarkSpots();
      for (int i = 0; i < mySpots.size(); i++) {
        MarkSpot markSpot = mySpots.get(i);

        int yStart = markSpot.yStart;
        RangeHighlighter mark = markSpot.highlighters.get(markSpot.highlighters.size()-1);

        int yEnd = markSpot.yEnd;

        final Color color = mark.getErrorStripeMarkColor();

        int x = 1;
        int paintWidth = width;
        if (mark.isThinErrorStripeMark()) {
          paintWidth /= 2;
          x += paintWidth / 2;
        }

        g.setColor(color);
        g.fillRect(x+1, yStart, paintWidth - 1, yEnd-yStart);

        Color brighter = color.brighter();
        Color darker = color.darker();

        g.setColor(brighter);
        //left
        g.drawLine(x, yStart, x, yEnd-1);
        if (i == 0 || !isAdjacent(mySpots.get(i - 1), markSpot) || wider(markSpot, mySpots.get(i - 1))) {
          //top decoration
          g.drawLine(x + 1, yStart, x + paintWidth - 1, yStart);
        }
        g.setColor(darker);
        if (i == mySpots.size()-1 || !isAdjacent(markSpot, mySpots.get(i + 1)) || wider(markSpot, mySpots.get(i + 1))) {
          // bottom decoration
          g.drawLine(x + 1, yEnd-1, x + paintWidth-1, yEnd-1);
        }
        //right
        g.drawLine(x + paintWidth, yStart, x + paintWidth, yEnd - 1);

      }
    }

    private boolean isAdjacent(MarkSpot markTop, MarkSpot markBottom) {
      return markTop.yEnd >= markBottom.yStart;
    }
    private boolean wider(MarkSpot markTop, MarkSpot markBottom) {
      final RangeHighlighter highlighterTop = markTop.highlighters.get(markTop.highlighters.size() - 1);
      final RangeHighlighter highlighterBottom = markBottom.highlighters.get(markBottom.highlighters.size() - 1);
      return !highlighterTop.isThinErrorStripeMark() && highlighterBottom.isThinErrorStripeMark();
    }

    public void doClick(final MouseEvent e, final int width) {
      recalcMarkSpots();
      RangeHighlighter marker = getNearestRangeHighlighter(e, width);
      if (marker == null) return;
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

    private RangeHighlighter getNearestRangeHighlighter(final MouseEvent e, final int width) {
      List<MarkSpot> nearestSpots = getNearestMarkSpots(e, width);
      RangeHighlighter nearestMarker = null;
      int yPos = 0;
      for (int i = 0; i < nearestSpots.size(); i++) {
        MarkSpot markSpot = nearestSpots.get(i);
        for (int j = 0; j < markSpot.highlighters.size(); j++) {
          RangeHighlighter highlighter = markSpot.highlighters.get(j);
          final int newYPos = visibleLineToYPosition(offsetToLine(highlighter.getStartOffset()));

          if (nearestMarker == null || Math.abs(yPos - e.getY()) > Math.abs(newYPos - e.getY())) {
            nearestMarker = highlighter;
            yPos = newYPos;
          }
        }
      }
      return nearestMarker;
    }

    private List<MarkSpot> getNearestMarkSpots(final MouseEvent e, final double width) {
      List<MarkSpot> nearestSpot = new SmartList<MarkSpot>();
      for (int i = 0; i < mySpots.size(); i++) {
        MarkSpot markSpot = mySpots.get(i);
        if (markSpot.near(e, width)) {
          nearestSpot.add(markSpot);
        }
      }
      return nearestSpot;
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

  private int visibleLineToYPosition(int lineNumber) {
    EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
    int top = scrollBar.getDecScrollButtonHeight() + 1;
    int bottom = scrollBar.getIncScrollButtonHeight();
    final int targetHeight = myScrollBarHeight - top - bottom;
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
