package com.intellij.ui;

import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

/**
 * @author Eugene Belyaev
 */
public class HoverHyperlinkLabel extends JLabel {
  private String myOriginalText;
  private ArrayList<HyperlinkListener> myListeners = new ArrayList<HyperlinkListener>();

  public HoverHyperlinkLabel(String text) {
    this(text, Color.BLUE);
  }

  public HoverHyperlinkLabel(String text, Color color) {
    super(text);
    myOriginalText = text;
    setForeground(color);
    setupListener();
  }

  private void setupListener() {
    addMouseListener(new MouseHandler());
  }

  public void setText(String text) {
    if (BasicHTML.isHTMLString(getText())) { // if is currently showing string as html
      super.setText(underlineTextInHtml(text));
    }
    else {
      super.setText(text);
    }
    myOriginalText = text;
  }

  @NonNls private String underlineTextInHtml(final String text) {
    return "<html><u>" + text + "</u></html>";
  }

  public String getOriginalText() {
    return myOriginalText;
  }

  private class MouseHandler extends MouseAdapter {
    public void mouseClicked(MouseEvent e) {
      HyperlinkListener[] listeners = myListeners.toArray(new HyperlinkListener[myListeners.size()]);
      HyperlinkEvent event = new HyperlinkEvent(HoverHyperlinkLabel.this, HyperlinkEvent.EventType.ACTIVATED, null);
      for (int i = 0; i < listeners.length; i++) {
        HyperlinkListener listener = listeners[i];
        listener.hyperlinkUpdate(event);
      }
    }

    public void mouseEntered(MouseEvent e) {
      HoverHyperlinkLabel.super.setText(underlineTextInHtml(myOriginalText));
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public void mouseExited(MouseEvent e) {
      HoverHyperlinkLabel.super.setText(myOriginalText);
      setCursor(Cursor.getDefaultCursor());
    }
  }

  public void addHyperlinkListener(HyperlinkListener listener) {
    myListeners.add(listener);
  }

  public void removeHyperlinkListener(HyperlinkListener listener) {
    myListeners.remove(listener);
  }
}
