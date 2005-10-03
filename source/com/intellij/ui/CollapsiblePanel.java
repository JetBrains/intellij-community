package com.intellij.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 29
 * @author 2003
 */
public class CollapsiblePanel extends JPanel {
  private final JButton myToggleCollapseButton;
  private final JComponent myContent;
  private boolean myIsCollapsed;
  private final Collection<CollapsingListener> myListeners = new ArrayList<CollapsingListener>();
  private boolean myIsInitialized = false;
  private final Icon myExpandIcon;
  private final Icon myCollapseIcon;

  public CollapsiblePanel(JComponent content, boolean collapseButtonAtLeft, boolean isCollapsed, Icon collapseIcon, Icon expandIcon) {
    super(new GridBagLayout());
    myContent = content;
    this.setBackground(content.getBackground());
    myExpandIcon = expandIcon;
    myCollapseIcon = collapseIcon;
    final Dimension buttonDimension = getButtonDimension();
    myToggleCollapseButton = new JButton();
    myToggleCollapseButton.setSize(buttonDimension);
    myToggleCollapseButton.setPreferredSize(buttonDimension);
    myToggleCollapseButton.setMinimumSize(buttonDimension);
    myToggleCollapseButton.setMaximumSize(buttonDimension);

    this.add(myToggleCollapseButton,
             new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0,
                                    collapseButtonAtLeft ? GridBagConstraints.WEST : GridBagConstraints.EAST,
                                    GridBagConstraints.NONE,
                                    new Insets(-5, collapseButtonAtLeft ? 0 : -5, 0, collapseButtonAtLeft ? -5 : 0), 0,
                                    0));
    myToggleCollapseButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setCollapsed(!myIsCollapsed);
      }
    });
    setCollapsed(isCollapsed);

  }

  private Dimension getButtonDimension() {
    if (myExpandIcon == null)
      return new Dimension(7, 7);
    else
      return new Dimension(myExpandIcon.getIconWidth(), myExpandIcon.getIconHeight());
  }

  public CollapsiblePanel(JComponent content, boolean collapseButtonAtLeft) {
    this(content, collapseButtonAtLeft, false, null, null);
  }

  protected void setCollapsed(boolean collapse) {
    try {
      if (collapse) {
        if (myIsInitialized) this.remove(myContent);
      }
      else {
        this.add(myContent,
                 new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                        new Insets(0, 0, 0, 0), 0, 0));
      }
      myIsCollapsed = collapse;

      Icon icon = getIcon();
      if (icon != null){
        myToggleCollapseButton.setIcon(icon);
        myToggleCollapseButton.setBorder(null);
        myToggleCollapseButton.setBorderPainted(false);
        myToggleCollapseButton.setToolTipText(getToggleButtonToolTipText());
      }

      notifyListners();

      revalidate();
      repaint();
    }
    finally {
      myIsInitialized = true;
    }
  }

  private String getToggleButtonToolTipText() {
    if (myIsCollapsed)
      return UIBundle.message("collapsible.panel.collapsed.state.tooltip.text");
    else
      return UIBundle.message("collapsible.panel.expanded.state.tooltip.text");
  }

  private Icon getIcon() {
    if (myIsCollapsed)
      return myExpandIcon;
    else
      return myCollapseIcon;
  }

  private void notifyListners() {
    CollapsingListener[] listeners = myListeners.toArray(new CollapsingListener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].onCollapsingChanged(this, isCollapsed());
    }
  }

  public void addCollapsingListener(CollapsingListener listener) {
    myListeners.add(listener);
  }

  public void removeCollapsingListener(CollapsingListener listener) {
    myListeners.remove(listener);
  }

  public boolean isCollapsed() {
    return myIsCollapsed;
  }
}
