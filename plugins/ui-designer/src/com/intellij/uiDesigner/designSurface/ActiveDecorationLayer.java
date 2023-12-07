// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.uiDesigner.SelectionWatcher;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.PlatformColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Decoration layer is over COMPONENT_LAYER (layer where all components are located).
 * It contains all necessary decorators. Decorators are:
 * - special mini-buttons to perform editing of grids (add/remove of columns)
 */
final class ActiveDecorationLayer extends JComponent implements FeedbackLayer {
  private static final Logger LOG = Logger.getInstance(ActiveDecorationLayer.class);

  private final GuiEditor myEditor;
  private final JToolTip myToolTip;

  private final Map<RadComponent, ListenerNavigateButton> myNavigateButtons = new HashMap<>();

  private final FeedbackPainterPanel myFeedbackPainterPanel = new FeedbackPainterPanel();
  private final RectangleFeedbackPainter myRectangleFeedbackPainter = new RectangleFeedbackPainter();

  ActiveDecorationLayer(final @NotNull GuiEditor editor) {
    myEditor = editor;
    myToolTip = new JToolTip();
  }

  public void installSelectionWatcher() {
    new MyNavigateButtonSelectionWatcher(myEditor).setupListeners();
  }

  @Override
  public void paint(final Graphics g){
    layoutListenerNavigateButtons();

    // Paint active decorators
    paintChildren(g);
  }

  private void layoutListenerNavigateButtons() {
    for(Map.Entry<RadComponent, ListenerNavigateButton> e: myNavigateButtons.entrySet()) {
      RadComponent c = e.getKey();
      ListenerNavigateButton btn = e.getValue();
      if (btn.isVisible()) {
        Rectangle rc = SwingUtilities.convertRectangle(c.getDelegee().getParent(), c.getBounds(), this);
        btn.setLocation(rc.x, rc.y+rc.height);
      }
    }
  }

  @Override
  public void putFeedback(Component relativeTo, final Rectangle rc, final String tooltipText) {
    putFeedback(relativeTo, rc, myRectangleFeedbackPainter, tooltipText);
  }

  @Override
  public void putFeedback(Component relativeTo, Rectangle rc, final FeedbackPainter feedbackPainter, final String tooltipText) {
    rc = SwingUtilities.convertRectangle(relativeTo, rc, this);
    myFeedbackPainterPanel.setBounds(rc);
    myFeedbackPainterPanel.setPainter(feedbackPainter != null ? feedbackPainter : myRectangleFeedbackPainter);
    Point pntMouse = myEditor.getGlassLayer().getLastMousePosition();
    putToolTip(this, new Point(pntMouse.x+20, pntMouse.y+30), tooltipText);
    if (myFeedbackPainterPanel.getParent() != this) {
      add(myFeedbackPainterPanel);
      repaint();
    }
  }

  private void putToolTip(Component relativeTo, Point pnt, @Nullable @NlsSafe String text) {
    if (text == null) {
      if (myToolTip.getParent() == this) {
        remove(myToolTip);
        repaint();
      }
    }
    else {
      String oldText = myToolTip.getTipText();
      myToolTip.setTipText(text);

      pnt = SwingUtilities.convertPoint(relativeTo, pnt, this);
      Dimension prefSize = myToolTip.getPreferredSize();
      pnt.x = Math.min(pnt.x, getBounds().width - prefSize.width);
      pnt.y = Math.min(pnt.y, getBounds().height - prefSize.height);
      myToolTip.setBounds(pnt.x, pnt.y, prefSize.width, prefSize.height);
      if (myToolTip.getParent() != this) {
        add(myToolTip);
        repaint();
      }
      else if (!text.equals(oldText)) {
        myToolTip.repaint();
      }
    }
  }

  @Override
  public void removeFeedback() {
    boolean needRepaint = false;
    if (myFeedbackPainterPanel.getParent() == this) {
      remove(myFeedbackPainterPanel);
      needRepaint = true;
    }
    if (myToolTip.getParent() == this) {
      remove(myToolTip);
      needRepaint = true;
    }
    if (needRepaint) repaint();
  }

  private static class RectangleFeedbackPainter implements FeedbackPainter {

    @Override
    public void paintFeedback(Graphics2D g2d, Rectangle rc) {
      g2d.setColor(PlatformColors.BLUE);
      g2d.setStroke(new BasicStroke(2.5f));
      // give space for stroke to be painted
      g2d.drawRect(rc.x+1, rc.y+1, rc.x+rc.width-2, rc.y+rc.height-2);
    }
  }

  private static class FeedbackPainterPanel extends JPanel {
    private FeedbackPainter myFeedbackPainter;

    FeedbackPainterPanel() {
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g;
      final Stroke savedStroke = g2d.getStroke();
      final Color savedColor = g2d.getColor();
      try {
        myFeedbackPainter.paintFeedback(g2d, new Rectangle(0, 0, getWidth(), getHeight()));
      }
      finally {
        g2d.setStroke(savedStroke);
        g2d.setColor(savedColor);
      }
    }

    public void setPainter(final FeedbackPainter feedbackPainter) {
      myFeedbackPainter = feedbackPainter;
    }
  }

  private class MyNavigateButtonSelectionWatcher extends SelectionWatcher {
    MyNavigateButtonSelectionWatcher(final GuiEditor editor) {
      super(editor);
    }

    @Override
    protected void selectionChanged(RadComponent component, boolean selected) {
      ListenerNavigateButton btn = myNavigateButtons.get(component);
      if (selected) {
        ReadAction.nonBlocking(() -> component.getBinding() != null ? ListenerNavigateButton.prepareActionGroup(component) : null)
          .finishOnUiThread(ModalityState.nonModal(), group -> {
            if (group != null && group.getChildrenCount() > 0) {
              ListenerNavigateButton navigateButton = btn;
              if (navigateButton == null) {
                navigateButton = new ListenerNavigateButton(component);
                myNavigateButtons.put(component, navigateButton);
              }
              add(navigateButton);
              navigateButton.setVisible(true);
            }
            else {
              if (btn != null) {
                btn.setVisible(false);
              }
            }
          })
          .submit(AppExecutorUtil.getAppExecutorService());
      }
      else {
        if (btn != null) {
          btn.setVisible(false);
        }
      }
    }
  }
}
