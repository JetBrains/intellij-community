package com.intellij.openapi.diff.impl;

import com.intellij.openapi.diff.impl.splitter.DiffDividerPaint;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.ui.Splitter;

import javax.swing.*;
import java.awt.*;

class DiffSplitter extends Splitter {
  private final DiffDividerPaint myPaint;

  private final VisibleAreaListener myVisibleAreaListener = new VisibleAreaListener() {
        public void visibleAreaChanged(VisibleAreaEvent e) {
          redrawDiffs();
        }
      };

  public DiffSplitter(JComponent component1, JComponent component2, DiffDividerPaint dividerPaint) {
    myPaint = dividerPaint;
    setDividerWidth(30);
    setFirstComponent(component1);
    setSecondComponent(component2);
  }

  protected Splitter.Divider createDivider() {
    return new Divider(){
      public void paint(Graphics g) {
        super.paint(g);
        myPaint.paint(g, this);
      }
    };
  }

  public void redrawDiffs() {
    getDivider().repaint();
  }

  public VisibleAreaListener getVisibleAreaListener() {
    return myVisibleAreaListener;
  }
}
