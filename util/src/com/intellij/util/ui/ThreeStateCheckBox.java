package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;

/**
 * @author spleaner
 */
public class ThreeStateCheckBox extends JCheckBox {
  private State myState;

  public static enum State {
    SELECTED, NOT_SELECTED, DONT_CARE
  }

  public ThreeStateCheckBox() {
    this(null, null, State.DONT_CARE);
  }

  public ThreeStateCheckBox(final State initial) {
    this(null, null, initial);
  }

  public ThreeStateCheckBox(final String text) {
    this(text, null, State.DONT_CARE);
  }

  public ThreeStateCheckBox(final String text, final State initial) {
    this(text, null, initial);
  }

  public ThreeStateCheckBox(final String text, final Icon icon) {
    this(text, icon, State.DONT_CARE);
  }

  public ThreeStateCheckBox(final String text, final Icon icon, final State initial) {
    super(text, icon);

    setModel(new ToggleButtonModel() {
      @Override
      public void setSelected(boolean selected) {
        myState = nextState();
        fireStateChanged();
        fireItemStateChanged(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, this, ItemEvent.SELECTED));
      }

      @Override
      public boolean isSelected() {
        return myState == State.SELECTED;
      }
    });

    setState(initial);
  }

  private State nextState() {
    switch (myState) {
      case SELECTED:
        return State.NOT_SELECTED;
      case NOT_SELECTED:
        return State.DONT_CARE;
      default:
        return State.SELECTED;
    }
  }

  @Override
  public void setSelected(final boolean b) {
    setState(b ? State.SELECTED : State.NOT_SELECTED);
  }

  public void setState(State state) {
    myState = state;
    repaint();
  }

  public State getState() {
    return myState;
  }

  @Override
  public void paint(final Graphics g) {
    super.paint(g);
    switch (getState()) {
      case DONT_CARE:
        final Rectangle r = getBounds();
        final Insets i = getInsets();

        Icon icon = getIcon();
        if (icon == null) {
          icon = UIManager.getIcon("CheckBox.icon");
        }

        if (icon != null) {
          //final Color selected = UIManager.getColor("CheckBox.focus");
          //if (selected != null) {
          //  g.setColor(selected);
          //}

          final int width1 = icon.getIconWidth();
          final int height1 = r.height - i.top - i.bottom;
          final int yoffset = height1 / 2 - 1;
          final int xoffset = width1 / 2 - width1 / 5;

          g.fillRect(xoffset + i.left, yoffset + i.top, width1 / 3, 2);
        }
        break;
      default:
        break;
    }
  }
}
