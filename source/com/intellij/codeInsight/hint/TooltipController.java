package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.DocumentFragment;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.SplittingUtil;
import com.intellij.util.Alarm;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class TooltipController {
  private LightweightHint myCurrentTooltip;
  private Object myCurrentTooltipObject;
  private TooltipGroup myCurrentTooltipGroup;
  private Alarm myTooltipAlarm = new Alarm();

  public void cancelTooltips() {
    myTooltipAlarm.cancelAllRequests();
    hideCurrentTooltip();
  }

  public void cancelTooltip(TooltipGroup groupId) {
    if (groupId.equals(myCurrentTooltipGroup)) {
      cancelTooltips();
    }
  }

  public void showTooltipByMouseMove(final Editor editor,
                                     MouseEvent e,
                                     final Object tooltipObject,
                                     final boolean alignToRight, final TooltipGroup group) {
    myTooltipAlarm.cancelAllRequests();
    if (myCurrentTooltip == null || !myCurrentTooltip.isVisible()) {
      myCurrentTooltipObject = null;
    }

    if (Comparing.equal(tooltipObject, myCurrentTooltipObject)) {
      return;
    }
    hideCurrentTooltip();

    if (tooltipObject != null) {
      final Point p = SwingUtilities.convertPoint(
        (Component)e.getSource(),
        e.getPoint(),
        editor.getComponent().getRootPane().getLayeredPane()
      );
      p.x += alignToRight ? -10 : 10;

      myTooltipAlarm.addRequest(
        new Runnable() {
          public void run() {
            if (editor.getContentComponent().isShowing()) {
              showTooltip(editor, p, tooltipObject, alignToRight, group);
            }
          }
        },
        50
      );
    }
  }

  private void hideCurrentTooltip() {
    if (myCurrentTooltip != null) {
      myCurrentTooltip.hide();
      myCurrentTooltip = null;
      myCurrentTooltipGroup = null;
    }
  }

  /**
   * @param p     point in layered pane coordinate system
   * @param group
   */
  public void showTooltip(final Editor editor, Point p, Object tooltipObject, boolean alignToRight, TooltipGroup group) {
    myTooltipAlarm.cancelAllRequests();
    if (myCurrentTooltip == null || !myCurrentTooltip.isVisible()) {
      myCurrentTooltipObject = null;
    }

    if (Comparing.equal(tooltipObject, myCurrentTooltipObject)) return;
    if (myCurrentTooltipGroup != null && group.compareTo(myCurrentTooltipGroup) < 0) return;

    p = new Point(p);
    hideCurrentTooltip();

    myCurrentTooltipGroup = group;

    final HintManager hintManager = HintManager.getInstance();
    LightweightHint hint;

    final JComponent editorComponent = editor.getComponent();
    if (tooltipObject instanceof DocumentFragment) {
      DocumentFragment fragment = (DocumentFragment)tooltipObject;

      TextRange range = fragment.getTextRange();
      int startOffset = range.getStartOffset();
      int endOffset = range.getEndOffset();
      Document doc = fragment.getDocument();
      int endLine = doc.getLineNumber(endOffset);
      int startLine = doc.getLineNumber(startOffset);

      JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

      p = editor.logicalPositionToXY(new LogicalPosition(startLine, 0));
      p = SwingUtilities.convertPoint(
        ((EditorEx)editor).getGutterComponentEx(),
        p,
        layeredPane
      );

      p.x -= 3;
      p.y += editor.getLineHeight();

      Point screen = new Point(p);
      SwingUtilities.convertPointToScreen(screen, layeredPane);
      int maxLineCount = (Toolkit.getDefaultToolkit().getScreenSize().height - screen.y) / editor.getLineHeight();

      if (endLine - startLine > maxLineCount) {
        endOffset = doc.getLineEndOffset(Math.min(startLine + maxLineCount, doc.getLineCount() - 1));
      }

      FoldingModelEx foldingModel = (FoldingModelEx)editor.getFoldingModel();
      foldingModel.setFoldingEnabled(false);
      TextRange textRange = new TextRange(startOffset, endOffset);
      hint = EditorFragmentComponent.showEditorFragmentHintAt(editor, textRange, p.x, p.y, false, false);
      foldingModel.setFoldingEnabled(true);
    }
    else {
      JLabel label = new JLabel();
      final JComponent contentComponent = editor.getContentComponent();
      // This listeners makes hint transparent for mouse events. It means that hint is closed
      // by MousePressed and this MousePressed goes into the underlying editor component.
      label.addMouseListener(
        new MouseAdapter() {
          public void mousePressed(MouseEvent e) {
            MouseEvent newMouseEvent = SwingUtilities.convertMouseEvent(e.getComponent(), e, contentComponent);
            hintManager.hideAllHints();
            contentComponent.dispatchEvent(newMouseEvent);
          }
        }
      );

      label.setBorder(
        BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(Color.black),
          BorderFactory.createEmptyBorder(0, 5, 0, 5)
        )
      );
      label.setForeground(Color.black);
      label.setBackground(HintUtil.INFORMATION_COLOR);
      label.setOpaque(true);

      String text;
      if (tooltipObject instanceof HighlightInfo) {
        HighlightInfo info = (HighlightInfo)tooltipObject;
        text = info.toolTip;
      }
      else {
        text = tooltipObject.toString();
      }

      if (text == null) return;
      label.setText(text);
      int width = label.getPreferredSize().width;

      JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();

      int widthLimit = layeredPane.getWidth() - 10;
      int heightLimit = layeredPane.getHeight() - 5;
      if (text.indexOf("<html>") < 0 && width > widthLimit / 3) {
        label.setUI(new MultiLineLabelUI());
        text = splitText(label, text, widthLimit);
        label.setText(text);
      }

      if (alignToRight) {
        p.x -= label.getPreferredSize().width;
      }

      // try to make cursor outside tooltip. SCR 15038
      p.x += 3;
      p.y += 3;
      width = label.getPreferredSize().width;
      if (p.x + width >= widthLimit) {
        p.x = widthLimit - width;
      }
      if (p.x < 3) {
        p.x = 3;
      }

      int height = label.getPreferredSize().height;
      if (p.y + height > heightLimit) {
        p.y = heightLimit - height;
      }
      hint = new LightweightHint(label);
      hintManager.showEditorHint(hint, editor, p,
                                 HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_OTHER_HINT |
                                 HintManager.HIDE_BY_SCROLLING, 0, false);
    }

    myCurrentTooltip = hint;
    myCurrentTooltipObject = tooltipObject;
  }

  /**
   * @return text splitted with '\n'
   */
  private static String splitText(JLabel label, String text, int widthLimit) {
    FontMetrics fontMetrics = label.getFontMetrics(label.getFont());

    String[] lines = SplittingUtil.splitText(text, fontMetrics, widthLimit, ' ');

    StringBuffer result = new StringBuffer();
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (i > 0) {
        result.append('\n');
      }
      result.append(line);
    }
    return result.toString();
  }
}