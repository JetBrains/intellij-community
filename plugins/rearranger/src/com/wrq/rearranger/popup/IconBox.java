/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.popup;

import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.Constraints;
import com.wrq.rearranger.util.IconUtil;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;

/**
 * Contains enough information to construct a checkbox with the given icon and tooltip
 * text, related to a setting.
 */
abstract class IconBox {

  static final Color mouseoverColor = new Color(181, 190, 214);
  static final Color selectedColor  = new Color(132, 146, 189);
  static final Color outlineColor   = new Color(8, 36, 107);

  final JPanel             containerPanel;
  final GridBagConstraints constraints;
  final IHasScrollPane     scrollPaneComponent;

  protected IconBox(JPanel containerPanel,
                    GridBagConstraints constraints,
                    IHasScrollPane scrollPaneComponent)
  {
    this.containerPanel = containerPanel;
    this.constraints = constraints;
    this.scrollPaneComponent = scrollPaneComponent;
  }

  abstract boolean getSetting();

  abstract void setSetting(boolean value);

  abstract Icon getIcon();

  abstract String getToolTipText();

  abstract int getShortcut();

  class MouseBox
    extends JCheckBox
  {
    boolean mouseOver;

    public MouseBox(Icon icon) {
      super(icon);
    }
  }

  class MyAction
    extends AbstractAction
  {
    final MouseBox box;

    public MyAction(String name, MouseBox box) {
      super(name);
      this.box = box;
    }

    public void actionPerformed(ActionEvent e) {
      //To change body of implemented methods use File | Settings | File Templates.
      box.getActionMap().get("pressed").actionPerformed(e);
      box.getActionMap().get("released").actionPerformed(e);
    }
  }

  public JPanel getIconBox() {
    final JPanel result = new JPanel(new GridBagLayout());

    final Icon icon = getIcon();
    final MouseBox box = new MouseBox(icon);
    box.setToolTipText(getToolTipText());
    box.setSelected(getSetting());
    box.setBackground(box.isSelected() ? selectedColor : null);
    setOutline(result, box.isSelected());
    final MyAction myAction = new MyAction("toggleButton", box);
    box.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          setAction(box, (JPanel)box.getParent());
        }
      }
    );
    box.setFocusable(false);
    box.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
      KeyStroke.getKeyStroke(
        getShortcut(),
        InputEvent.ALT_DOWN_MASK
      ),
      "toggleButton"
    );

    box.getActionMap().put("toggleButton", myAction);
    box.addMouseListener(
      new MouseAdapter() {
        public void mouseEntered(MouseEvent e) {
          box.mouseOver = true;
          if (!box.isSelected()) {
            box.setBackground(mouseoverColor);
            setOutline(result, true);
          }
        }

        public void mouseExited(MouseEvent e) {
          box.mouseOver = false;
          if (!box.isSelected()) {
            box.setBackground(null);
            setOutline(result, false);
          }
        }
      }
    );
    result.add(box);
    return result;
  }

  private void setAction(final MouseBox box, final JPanel result) {
    setSetting(box.isSelected());
    box.setBackground(box.isSelected() ? selectedColor : box.mouseOver ? mouseoverColor : null);
    setOutline(result, box.mouseOver);
    int lastIndex = containerPanel.getComponentCount() - 1;
    containerPanel.remove(lastIndex);
    final JScrollPane scrollPane = scrollPaneComponent.getScrollPane();
    containerPanel.add(scrollPane, constraints, lastIndex);
    scrollPane.invalidate();
    containerPanel.validate();
  }

  /**
   * draws a nearly-black outline border around the button (or removes such border).
   *
   * @param panel   JPanel which contains the button.
   * @param outline if true, draw the outline.
   */
  private void setOutline(JPanel panel, boolean outline) {
    Color borderColor = outline ? outlineColor : ((java.awt.Color)panel.getBackground());
    Border b = BorderFactory.createLineBorder(borderColor);
    panel.setBorder(b);
  }

  public static void main(String[] args) {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
    catch (Exception e) {
    }

    final JFrame frame = new JFrame("IconBox test");
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightedLastRow();
    frame.getContentPane().setLayout(new GridBagLayout());
    JPanel panel = new JPanel(new GridBagLayout());
    frame.getContentPane().add(panel, constraints.weightedLastCol());
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridheight = 1;
    constraints.weighty = 0;
    IHasScrollPane scroll = new IHasScrollPane() {
      public JScrollPane getScrollPane() {
        return new JScrollPane(new JLabel("scroll pane contents"));
      }
    };
    final RearrangerSettings settings = new RearrangerSettings();
    IconBox box = new IconBox(panel, constraints, scroll) {
      boolean getSetting() {
        return settings.isShowComments();
      }

      void setSetting(boolean value) {
        settings.setShowComments(value);
      }

      Icon getIcon() {
        return IconUtil.getIcon("ShowComments");
      }

      String getToolTipText() {
        return "Show comments (Alt+C)";
      }

      int getShortcut() {
        return KeyEvent.VK_C;
      }
    };
    panel.add(box.getIconBox(), constraints);
    constraints.weightedLastRow();
    constraints.fill = GridBagConstraints.BOTH;
    JScrollPane firstPane = new JScrollPane(new JLabel("first scrollpane"));
    panel.add(firstPane, constraints);

    //Finish setting up the frame, and show it.
    frame.addWindowListener(
      new WindowAdapter() {
        public void windowClosing(final WindowEvent e) {
          System.exit(0);
        }
      }
    );
    frame.pack();
    frame.setVisible(true);
  }
}

