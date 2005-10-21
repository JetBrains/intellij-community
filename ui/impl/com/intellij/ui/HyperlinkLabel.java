package com.intellij.ui;

import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public class HyperlinkLabel extends HighlightableComponent {
  private HighlightedText myHighlightedText;
  private List<HyperlinkListener> myListeners = new ArrayList<HyperlinkListener>();
  private final Color myTextForegroundColor;
  private final Color myTextBackgroundColor;
  private final Color myTextEffectColor;

  public HyperlinkLabel(String text) {
    this(text, Color.BLUE, UIUtil.getLabelBackground(), Color.BLUE);
  }

  public HyperlinkLabel(String text, final Color textForegroundColor, final Color textBackgroundColor, final Color textEffectColor) {
    myTextForegroundColor = textForegroundColor;
    myTextBackgroundColor = textBackgroundColor;
    myTextEffectColor = textEffectColor;
    enforceBackgroundOutsideText(textBackgroundColor);
    setHyperlinkText(text);
    enableEvents(AWTEvent.MOUSE_EVENT_MASK);
  }

  public void addNotify() {
    super.addNotify();
    adjustSize();
  }

  public void setHyperlinkText(String text) {
    prepareText(text, myTextForegroundColor, myTextBackgroundColor, myTextEffectColor);
    revalidate();
    adjustSize();
  }

  private void adjustSize() {
    final Dimension preferredSize = this.getPreferredSize();
    this.setMinimumSize(preferredSize);
  }


  protected void processMouseEvent(MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_ENTERED) {
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    else if (e.getID() == MouseEvent.MOUSE_EXITED) {
      setCursor(Cursor.getDefaultCursor());
    }
    else if (e.getID() == MouseEvent.MOUSE_CLICKED) {
      fireHyperlinkEvent();
    }
    super.processMouseEvent(e);
  }

  private void prepareText(String text, final Color textForegroundColor, final Color textBackgroundColor, final Color textEffectColor) {
    setFont(UIUtil.getLabelFont());
    myHighlightedText = new HighlightedText();
    myHighlightedText.appendText(text, new TextAttributes(
      textForegroundColor, textBackgroundColor, textEffectColor, EffectType.LINE_UNDERSCORE, Font.PLAIN
    ));
    myHighlightedText.applyToComponent(this);
    adjustSize();
  }

  public void addHyperlinkListener(HyperlinkListener listener) {
    myListeners.add(listener);
  }

  public void removeHyperlinkListener(HyperlinkListener listener) {
    myListeners.remove(listener);
  }

  String getText() {
    return myHighlightedText.getText();
  }

  protected void fireHyperlinkEvent() {
    HyperlinkEvent e = new HyperlinkEvent(this, HyperlinkEvent.EventType.ACTIVATED, null, null);
    HyperlinkListener[] listeners = myListeners.toArray(new HyperlinkListener[myListeners.size()]);
    for (HyperlinkListener listener : listeners) {
      listener.hyperlinkUpdate(e);
    }
  }
}
