/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 19, 2002
 * Time: 2:56:43 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.codeInsight.daemon.impl.HighlightInfoComposite;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.ErrorStripeEvent;
import com.intellij.openapi.editor.ex.ErrorStripeListener;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.util.SmartList;
import com.intellij.lang.annotation.HighlightSeverity;

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

      Collections.sort(
        myCachedSortedHighlighters, new Comparator() {
          public int compare(Object o1, Object o2) {
            RangeHighlighter h1 = (RangeHighlighter)o1;
            RangeHighlighter h2 = (RangeHighlighter)o2;
            if (h1.getLayer() != h2.getLayer()) {
              return h2.getLayer() - h1.getLayer();
            }
            return h1.getStartOffset() - h2.getEndOffset();
          }
        }
      );
    }

    return myCachedSortedHighlighters;
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
      //EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
      //int scrollBarHeight = scrollBar.getSize().height;
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();

      if (lineCount == 0) {
        return;
      }

      getErrorMarkPileList().doClick(e, getWidth());
    }

    public void mouseMoved(MouseEvent e) {
      EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
      //int scrollBarHeight = scrollBar.getSize().height;
      int buttonHeight = scrollBar.getDecScrollButtonHeight();
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();
      if (lineCount == 0) {
        return;
      }

      if (e.getY() < buttonHeight && myErrorStripeRenderer != null) {
        String tooltipMessage = myErrorStripeRenderer.getTooltipMessage();
        showTooltip(e, tooltipMessage);
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

  private void showTooltip(MouseEvent e, final Object tooltipObject) {
    if (tooltipObject != null) {
      final TooltipController tooltipController = HintManager.getInstance().getTooltipController();
      tooltipController.showTooltipByMouseMove(myEditor, e, tooltipObject,
                                               myEditor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_RIGHT,
                                               ERROR_STRIPE_TOOLTIP_GROUP);
    }
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

      //final int height = Math.max(yEndPosition - yStartPosition, 2);

      final ErrorMarkPile prevPile = list.size() == 0 ? null : list.get(list.size() - 1);
      int prevPileEnd = prevPile == null ? -100 : prevPile.yEnd;
      if (yStartPosition - prevPileEnd < getMinHeight()) {
        prevPile.addMark(mark, yEndPosition);
      }
      else {
        final ErrorMarkPile pile = new ErrorMarkPile(yStartPosition);
        pile.addMark(mark, yEndPosition);
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

  private int getMinHeight() {
    return 10;
  }

  private class ErrorMarkPile {
    private int yStart;
    private int yEnd;
    private List<RangeHighlighter> markers = new ArrayList<RangeHighlighter>();
    private static final int MAX_TOOLTIP_LINES = 5;

    public ErrorMarkPile(final int yStart) {
      this.yStart = yStart;
    }

    public void addMark(final RangeHighlighter mark, int newYEnd) {
      markers.add(mark);
      if (newYEnd - yStart < getMinHeight()) {
        yEnd = yStart + getMinHeight();
      }
      else {
        yEnd = newYEnd;
      }
    }

    public void paint(final Graphics g, int width) {
      int y = yStart;
      for (int i = 0; i < markers.size(); i++) {
        RangeHighlighter mark = markers.get(i);

        int yEndPosition;
        if (i == markers.size()-1) {
          yEndPosition = yEnd;
        }
        else {
          int visEndLine = myEditor.logicalToVisualPosition(
            new LogicalPosition(mark.getDocument().getLineNumber(mark.getEndOffset()), 0)
          ).line;

          yEndPosition = visibleLineToYPosition(visEndLine, myScrollBarHeight);
        }

        final int height = yEndPosition - y;
        final Color color = mark.getErrorStripeMarkColor();
        g.setColor(color);

        int x = 1;
        if (mark.isThinErrorStripeMark()) {
          width /= 2;
          x += width / 2;
        }

        g.fillRect(x, y, width - 1, height);
        Color brighter = color.brighter();
        Color darker = color.darker();

        g.setColor(brighter);
        g.drawLine(x, y, x, y + height);
        if (i == 0) {
          g.drawLine(x + 1, y, x + width - 1, y);
        }
        g.setColor(darker);
        if (i == markers.size()-1) {
          g.drawLine(x + 1, y + height, x + width, y + height);
        }
        g.drawLine(x + width, y, x + width, y + height - 1);

        y = yEndPosition;
      }
    }

    public boolean doClick(final MouseEvent e, final double width) {
      if (inside(e, width)) {
        RangeHighlighter marker = markers.get(0);
        myEditor.getCaretModel().moveToOffset(marker.getStartOffset());
        myEditor.getSelectionModel().removeSelection();
        ScrollingModel scrollingModel = myEditor.getScrollingModel();
        scrollingModel.disableAnimation();
        scrollingModel.scrollToCaret(ScrollType.CENTER);
        scrollingModel.enableAnimation();
        fireErrorMarkerClicked(marker, e);
        return true;
      }
      return false;
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
      List<HighlightInfo> infos = new SmartList<HighlightInfo>();
      for (int i = 0; i < markers.size(); i++) {
        RangeHighlighter marker = markers.get(i);
        if (marker.getErrorStripeTooltip() instanceof HighlightInfo) {
          infos.add((HighlightInfo)marker.getErrorStripeTooltip());
        }
      }
      if (infos.size() == 0) {
        RangeHighlighter marker = markers.get(0);
        showTooltip(e, marker.getErrorStripeTooltip());
      }
      else {
        // need to show tooltips for multiple highlightinfos
        final int oldSize = infos.size();
        int moreErrors = 0;
        int moreWarnings = 0;
        if (oldSize > MAX_TOOLTIP_LINES) {
          for (int i = MAX_TOOLTIP_LINES; i < infos.size(); i++) {
            HighlightInfo info = infos.get(i);
            final HighlightSeverity severity = info.getSeverity();
            if (severity == HighlightSeverity.ERROR) {
              moreErrors++;
            }
            else {
              moreWarnings++;
            }
          }
          infos = infos.subList(0, MAX_TOOLTIP_LINES);
        }
        final HighlightInfoComposite composite = new HighlightInfoComposite(infos);
        if (moreErrors + moreWarnings != 0) {
          String line = "&nbsp;&nbsp;&nbsp;...";
          if (moreErrors != 0) {
            line += " and "+moreErrors + " more error" + (moreErrors == 1 ? "":"s");
          }
          if (moreWarnings != 0) {
            line += " and " + moreWarnings + " more warning" + (moreWarnings == 1 ? "" : "s");
          }
          line += "...";
          composite.addToolTipLine(line);
        }
        showTooltip(e, composite);
      }
      return true;
    }
  }
}
