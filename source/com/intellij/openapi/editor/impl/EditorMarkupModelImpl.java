/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 19, 2002
 * Time: 2:56:43 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoComposite;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.codeInsight.hint.TooltipController;
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

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class EditorMarkupModelImpl extends MarkupModelImpl implements EditorMarkupModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorMarkupModelImpl");

  private static final TooltipGroup ERROR_STRIPE_TOOLTIP_GROUP = new TooltipGroup("ERROR_STRIPE_TOOLTIP_GROUP", 0);

  private EditorImpl myEditor;
  private MyErrorPanel myErrorPanel;
  private ErrorStripeRenderer myErrorStripeRenderer = null;
  private ArrayList<ErrorStripeListener> myErrorMarkerListeners = new ArrayList<ErrorStripeListener>();
  private ErrorStripeListener[] myCachedErrorMarkerListeners = null;
  private ArrayList<RangeHighlighter> myCachedSortedHighlighters = null;

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
    if (myErrorPanel != null) {
      myErrorPanel.repaint();
    }
  }

  private ArrayList<RangeHighlighter> getSortedHighlighters() {
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
            return h1.getLayer() - h2.getLayer();
          }
        }
      );
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

        int scrollBarHeight = scrollBar.getSize().height;

        ArrayList<RangeHighlighter> sortedHighlighters = getSortedHighlighters();
        for (int i = 0; i < sortedHighlighters.size(); i++) {
          RangeHighlighter highlighter = sortedHighlighters.get(i);
          if (!highlighter.isValid()) continue;

          int visStartLine = myEditor.logicalToVisualPosition(
            new LogicalPosition(highlighter.getDocument().getLineNumber(highlighter.getStartOffset()), 0)
          ).line;

          int visEndLine = myEditor.logicalToVisualPosition(
            new LogicalPosition(highlighter.getDocument().getLineNumber(highlighter.getEndOffset()), 0)
          ).line;

          int yStartPosition = visibleLineToYPosition(visStartLine, scrollBarHeight);
          int yEndPosition = visibleLineToYPosition(visEndLine, scrollBarHeight);

          final int height = Math.max(yEndPosition - yStartPosition, 2);

          g.setColor(highlighter.getErrorStripeMarkColor());
          int width = getWidth();
          int x = 1;
          if (highlighter.isThinErrorStripeMark()) {
            width /= 2;
            x += width / 2;
          }

          g.fill3DRect(x, yStartPosition, width - 1, height, true);
        }
      }
      finally {
        ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
      }
    }

    private boolean checkLineMarker(RangeHighlighter marker, int scrollBarHeight, int m_x, int m_y) {
      if (!marker.isValid()) {
        return false;
      }

      Color color = marker.getErrorStripeMarkColor();
      if (color == null) {
        return false;
      }


      int visLine = myEditor.logicalToVisualPosition(
        new LogicalPosition(marker.getDocument().getLineNumber(marker.getStartOffset()), 0)
      ).line;

      int visEndLine = myEditor.logicalToVisualPosition(
        new LogicalPosition(marker.getDocument().getLineNumber(marker.getEndOffset()), 0)
      ).line;

      int y = visibleLineToYPosition(visLine, scrollBarHeight);
      int endY = visibleLineToYPosition(visEndLine, scrollBarHeight);
      if (m_x >= 0 && m_x <= getWidth() && m_y >= y && m_y <= y + Math.max(endY - y, 2)) {
        return true;
      }
      return false;
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
      EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
      int scrollBarHeight = scrollBar.getSize().height;
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();

      if (lineCount == 0) {
        return;
      }

      ArrayList<RangeHighlighter> sortedHighlighters = getSortedHighlighters();
      for (int i = sortedHighlighters.size() - 1; i >= 0; i--) {
        RangeHighlighter marker = sortedHighlighters.get(i);
        if (checkLineMarker(marker, scrollBarHeight, e.getX(), e.getY())) {
          myEditor.getCaretModel().moveToOffset(marker.getStartOffset());
          myEditor.getSelectionModel().removeSelection();
          ScrollingModel scrollingModel = myEditor.getScrollingModel();
          scrollingModel.disableAnimation();
          scrollingModel.scrollToCaret(ScrollType.CENTER);
          scrollingModel.enableAnimation();
          fireErrorMarkerClicked(marker, e);
          return;
        }
      }
    }

    public void mouseMoved(MouseEvent e) {
      EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
      int scrollBarHeight = scrollBar.getSize().height;
      int buttonHeight = scrollBar.getDecScrollButtonHeight();
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();

      if (lineCount == 0) {
        return;
      }

      if (e.getY() < buttonHeight && myErrorStripeRenderer != null) {
          String tooltipMessage = myErrorStripeRenderer.getTooltipMessage();
          if (tooltipMessage != null) {
              TooltipController tooltipController = HintManager.getInstance().getTooltipController();
              tooltipController.showTooltipByMouseMove(myEditor, e,
                      tooltipMessage,
                                                                                    myEditor.getVerticalScrollbarOrientation() ==
                                                                                    EditorEx.VERTICAL_SCROLLBAR_RIGHT,
                                                                                    ERROR_STRIPE_TOOLTIP_GROUP);
          }
          return;
      }

      ArrayList<RangeHighlighter> sortedHighlighters = getSortedHighlighters();
      RangeHighlighter highlighterForTooltip = null;
      ArrayList<RangeHighlighter> highlightersForTooltip = null;
      for (int i = sortedHighlighters.size() - 1; i >= 0; i--) {
        RangeHighlighter marker = sortedHighlighters.get(i);
        if (checkLineMarker(marker, scrollBarHeight, e.getX(), e.getY())) {
          if (highlighterForTooltip == null) {
            highlighterForTooltip = marker;
          }
          else {
            if (highlightersForTooltip == null) {
              highlightersForTooltip = new ArrayList<RangeHighlighter>();
              highlightersForTooltip.add(highlighterForTooltip);
            }
            highlightersForTooltip.add(marker);
          }
        }
      }
      if (highlightersForTooltip != null) {
        highlighterForTooltip = highlightersForTooltip.get(0);
        final ArrayList<HighlightInfo> infos = new ArrayList<HighlightInfo>();
        for (int i = 0; i < highlightersForTooltip.size(); i++) {
          RangeHighlighter marker = highlightersForTooltip.get(i);
          if (marker.getErrorStripeTooltip() instanceof HighlightInfo) {
            infos.add((HighlightInfo)marker.getErrorStripeTooltip());
          }
        }
        if (highlightersForTooltip.size() == infos.size()) {
          // need to show tooltips for multiple highlightinfos
          final HighlightInfoComposite composite = new HighlightInfoComposite(infos);
          setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          showTooltip(e, composite);
          return;
        }
      }
      if (highlighterForTooltip != null) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        showTooltip(e, highlighterForTooltip.getErrorStripeTooltip());
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

    private void showTooltip(MouseEvent e, final Object tooltipObject) {
      if (tooltipObject != null) {
        HintManager.getInstance().getTooltipController().showTooltipByMouseMove(myEditor, e, tooltipObject,
                                                                                myEditor.getVerticalScrollbarOrientation() ==
                                                                                EditorEx.VERTICAL_SCROLLBAR_RIGHT,
                                                                                ERROR_STRIPE_TOOLTIP_GROUP);
      }
    }

    public void mouseEntered(MouseEvent e) {
      //   this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
  }
}
