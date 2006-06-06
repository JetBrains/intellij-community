package com.intellij.uiDesigner.designSurface;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.uiDesigner.SelectionWatcher;
import com.intellij.uiDesigner.radComponents.RadComponent;
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
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class ActiveDecorationLayer extends JComponent implements FeedbackLayer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.ActiveDecorationLayer");

  private final GuiEditor myEditor;
  private JToolTip myToolTip;

  private Map<RadComponent, ListenerNavigateButton> myNavigateButtons = new HashMap<RadComponent, ListenerNavigateButton>();

  private final FeedbackPainterPanel myFeedbackPainterPanel = new FeedbackPainterPanel();
  private final RectangleFeedbackPainter myRectangleFeedbackPainter = new RectangleFeedbackPainter();

  public ActiveDecorationLayer(@NotNull final GuiEditor editor) {
    myEditor = editor;
    myToolTip = new JToolTip();
  }

  public void installSelectionWatcher() {
    new MyNavigateButtonSelectionWatcher(myEditor);
  }

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

  public void putFeedback(Component relativeTo, final Rectangle rc, final String tooltipText) {
    putFeedback(relativeTo, rc, myRectangleFeedbackPainter, tooltipText);
  }

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

  public void putToolTip(Component relativeTo, Point pnt, @Nullable String text) {
    if (text == null) {
      if (myToolTip.getParent() == this) {
        remove(myToolTip);
        repaint();
      }
    }
    else {
      pnt = SwingUtilities.convertPoint(relativeTo, pnt, this);
      Dimension prefSize = myToolTip.getPreferredSize();
      pnt.x = Math.min(pnt.x, getBounds().width - prefSize.width);
      pnt.y = Math.min(pnt.y, getBounds().height - prefSize.height);
      myToolTip.setBounds(pnt.x, pnt.y, prefSize.width, prefSize.height);
      myToolTip.setTipText(text);
      if (myToolTip.getParent() != this) {
        add(myToolTip);
        repaint();
      }
    }
  }

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

    public void paintFeedback(Graphics2D g2d, Rectangle rc) {
      g2d.setColor(Color.BLUE);
      g2d.setStroke(new BasicStroke(2.5f));
      // give space for stroke to be painted
      g2d.drawRect(rc.x+1, rc.y+1, rc.x+rc.width-2, rc.y+rc.height-2);
    }
  }

  private static class FeedbackPainterPanel extends JPanel {
    private FeedbackPainter myFeedbackPainter;

    public FeedbackPainterPanel() {
      setOpaque(false);
    }

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
    public MyNavigateButtonSelectionWatcher(final GuiEditor editor) {
      super(editor);
    }

    protected void selectionChanged(RadComponent component, boolean selected) {
      ListenerNavigateButton btn = myNavigateButtons.get(component);
      if (selected) {
        DefaultActionGroup group = component.getBinding() != null ? ListenerNavigateButton.prepareActionGroup(component) : null;
        if (group != null && group.getChildrenCount() > 0) {
          if (btn == null) {
            btn = new ListenerNavigateButton(component);
            myNavigateButtons.put(component, btn);
          }
          add(btn);
          btn.setVisible(true);
        }
        else {
          if (btn != null) {
            btn.setVisible(false);
          }
        }
      }
      else {
        if (btn != null) {
          btn.setVisible(false);
        }
      }
    }
  }
}
