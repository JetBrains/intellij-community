// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.laf.win10;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
import com.intellij.ide.ui.laf.darcula.ui.DarculaJBPopupComboPopup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.ColoredItem;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.PopupMenuEvent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Path2D;
import java.beans.PropertyChangeListener;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.MouseHoverPropertyTrigger;
import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.Outline;

/**
 * @author Konstantin Bulenkov
 */
public final class WinIntelliJComboBoxUI extends DarculaComboBoxUI {
  private static final String HOVER_PROPERTY = "JComboBox.mouseHover";
  private static final String PRESSED_PROPERTY = "JComboBox.mousePressed";
  private static final Border DEFAULT_EDITOR_BORDER = JBUI.Borders.empty(1, 0);
  private static final JBDimension ARROW_BUTTON_SIZE = new JBDimension(21, 24); // Count borders

  private MouseListener mouseListener;

  private MouseListener buttonReleaseListener;
  private MouseListener buttonHoverListener;

  private MouseListener editorHoverListener;

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new WinIntelliJComboBoxUI();
  }

  @Override
  protected void installListeners() {
    super.installListeners();

    if (!comboBox.isEditable()) {
      comboBox.addMouseListener(mouseListener = new ComboBoxMouseListener());
    }
  }

  @Override
  public void uninstallListeners() {
    super.uninstallListeners();
    comboBox.removeMouseListener(mouseListener);
  }

  @Override
  protected PropertyChangeListener createPropertyListener() {
    return e -> {
      if ("enabled".equals(e.getPropertyName())) {
        EditorTextField etf = UIUtil.findComponentOfType((JComponent)editor, EditorTextField.class);
        if (etf != null) {
          etf.setBackground(getComboBackground(true));
        }
      }
      else if ("editable".equals(e.getPropertyName())) {
        if (e.getNewValue() == Boolean.TRUE) {
          comboBox.removeMouseListener(mouseListener);
        }
        else {
          comboBox.addMouseListener(mouseListener);
        }
      }
    };
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      Rectangle r = new Rectangle(c.getSize());

      if (c.isOpaque()) {
        Container parent = c.getParent();
        if (parent != null) {
          g2.setColor(DarculaUIUtil.isTableCellEditor(c) && editor != null ? editor.getBackground() : parent.getBackground());
          g2.fill(r);
        }
      }

      boolean isOpaque = editor != null && editor.isOpaque();
      g2.setColor(getComboBackground(isOpaque));
      JBInsets.removeFrom(r, JBUI.insets(DarculaUIUtil.isTableCellEditor(c) ? 1 : 2));

      boolean applyAlpha = !(comboBox.isEnabled() || isOpaque && comboBox.isEditable());
      if (applyAlpha) {
        float alpha = comboBox.isEditable() ? 0.35f : 0.47f;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
      }

      g2.fill(r);

      if (!comboBox.isEditable()) {
        hasFocus = comboBox.hasFocus();
        paintCurrentValue(g2, rectangleForCurrentValue(), hasFocus);
      }
    }
    finally {
      g2.dispose();
    }
  }

  @Override
  protected Rectangle rectangleForCurrentValue() {
    int w = comboBox.getWidth();
    int h = comboBox.getHeight();
    Insets i = comboBox.getInsets();

    int buttonWidth = h;
    if (arrowButton != null) {
      buttonWidth = comboBox.getComponentOrientation().isLeftToRight() ?
                    arrowButton.getWidth() - i.right : arrowButton.getWidth() - i.left;
    }

    Rectangle rect = (comboBox.getComponentOrientation().isLeftToRight()) ?
                     new Rectangle(i.left, i.top, w - (i.left + i.right + buttonWidth), h - (i.top + i.bottom)) :
                     new Rectangle(i.left + buttonWidth, i.top, w - (i.left + i.right + buttonWidth), h - (i.top + i.bottom));

    JBInsets.removeFrom(rect, padding);
    rect.width += comboBox.isEditable() ? 0 : padding.right;
    return rect;
  }


  @Override
  public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
    @SuppressWarnings("unchecked")
    ListCellRenderer<Object> renderer = comboBox.getRenderer();
    Component c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false);

    c.setFont(comboBox.getFont());
    c.setForeground(comboBox.isEnabled() ? UIManager.getColor("Label.foreground") : UIManager.getColor("Label.disabledForeground"));

    Rectangle r = new Rectangle(bounds);
    JComponent jc = (JComponent)c;

    jc.setBorder(DEFAULT_EDITOR_BORDER);
    JBInsets.removeFrom(r, jc.getInsets());

    // paint selection in table-cell-editor mode correctly
    boolean changeOpaque = DarculaUIUtil.isTableCellEditor(comboBox) && c.isOpaque();
    if (changeOpaque) {
      jc.setOpaque(false);
    }
    else if (c.isOpaque()) {
      c.setBackground(getComboBackground(true));
    }

    currentValuePane.paintComponent(g, c, comboBox, r.x, r.y, r.width, r.height, c instanceof JPanel);

    // return opaque for combobox popup items painting
    if (changeOpaque) {
      jc.setOpaque(true);
    }
  }

  private Color getComboBackground(boolean opaque) {
    if (comboBox != null) {
      Color bg = comboBox.getBackground();
      Object value = comboBox.getSelectedItem();

      if (comboBox.isEnabled() && comboBox.isEditable() && editor != null) {
        return UIManager.getColor("TextField.background");
      }
      else if (!comboBox.isEnabled()) {
        return opaque ? UIManager.getColor("Button.background.opaque") : UIManager.getColor("Button.background");
      }
      else if (comboBox.isBackgroundSet() && !(bg instanceof UIResource)) {
        return bg;
      }
      else if (value instanceof ColoredItem) {
        return ((ColoredItem)value).getColor();
      }
      else if (!comboBox.isEditable()) {
        if (isPressed() || popup.isVisible()) {
          return UIManager.getColor("Button.intellij.native.pressedBackgroundColor");
        }
        else if (isHover()) {
          return UIManager.getColor("Button.intellij.native.focusedBackgroundColor");
        }
      }
    }
    return UIManager.getColor("Button.background");
  }

  @Override
  protected JButton createArrowButton() {
    JButton button = new BasicArrowButton(SwingConstants.SOUTH) {
      @Override
      public Dimension getPreferredSize() {
        return ARROW_BUTTON_SIZE;
      }

      @Override
      public void paint(Graphics g) {
        if (!UIUtil.isUnderWin10LookAndFeel()) return; // Paint events may still arrive after UI switch until entire UI is updated.

        Graphics2D g2 = (Graphics2D)g.create();
        try {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

          Rectangle outerRect = new Rectangle(getSize());
          JBInsets.removeFrom(outerRect, JBUI.insets(1));

          int bw = 1;
          Outline op = DarculaUIUtil.getOutline(comboBox);
          if (op != null) {
            op.setGraphicsColor(g2, hasFocus);
            bw = DarculaUIUtil.isTableCellEditor(comboBox) ? 1 : 2;
          }

          Rectangle innerRect = new Rectangle(outerRect);
          JBInsets.removeFrom(innerRect, JBUI.insets(bw));

          // paint background
          if (comboBox.isEditable() && comboBox.isEnabled()) {
            if (isPressed() || popup.isVisible()) {
              g2.setColor(UIManager.getColor("Button.intellij.native.pressedBackgroundColor"));
            }
            else if (comboBox.hasFocus() || isHover()) {
              g2.setColor(UIManager.getColor("Button.intellij.native.focusedBackgroundColor"));
            }
            else {
              g2.setColor(getComboBackground(false));
            }
            g2.fill(innerRect);
          }

          // paint border around button when combobox is editable
          if (comboBox.isEditable() && comboBox.isEnabled()) {
            Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
            border.append(outerRect, false);
            border.append(innerRect, false);

            if (getModel().isPressed() || popup.isVisible()) {
              g2.setColor(UIManager.getColor("Button.intellij.native.pressedBorderColor"));
              g2.fill(border);
            }
            else if (comboBox.hasFocus() || isHover()) {
              g2.setColor(UIManager.getColor("Button.intellij.native.focusedBorderColor"));
              g2.fill(border);
            }
          }

          Icon icon = getArrowIcon(this);
          int x = JBUIScale.scale(5);
          int y = (getHeight() - icon.getIconHeight()) / 2;
          icon.paintIcon(this, g2, x, y);
        }
        finally {
          g2.dispose();
        }
      }
    };

    button.setOpaque(false);

    buttonReleaseListener = new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        if (!comboBox.isEditable()) {
          comboBox.repaint();
        }
      }
    };

    buttonHoverListener = new MouseHoverPropertyTrigger(comboBox, HOVER_PROPERTY);

    button.addMouseListener(buttonHoverListener);
    button.addMouseListener(buttonReleaseListener);
    return button;
  }

  public static Icon getArrowIcon(@NotNull JComponent c) {
    return WinIconLookup.getIcon("comboDropTriangle", false, false, c.isEnabled());
  }

  @Override
  public void unconfigureArrowButton() {
    super.unconfigureArrowButton();
    if (arrowButton != null) {
      arrowButton.removeMouseListener(buttonReleaseListener);
      arrowButton.removeMouseListener(buttonHoverListener);
    }
  }


  @Override
  protected ComboBoxEditor createEditor() {
    ComboBoxEditor comboBoxEditor = new BasicComboBoxEditor.UIResource() {
      @Override
      protected JTextField createEditorComponent() {
        return new JTextField() {
          @Override
          public void setText(String s) {
            if (getText().equals(s)) {
              return;
            }
            super.setText(s);
          }

          @Override
          public Color getBackground() {
            return getComboBackground(false);
          }

          @Override
          public Border getBorder() {
            return DEFAULT_EDITOR_BORDER;
          }

          @Override
          public Insets getInsets() {
            return DEFAULT_EDITOR_BORDER.getBorderInsets(this);
          }

          @Override
          public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(size.width, Math.max(JBUIScale.scale(18), size.height));
          }
        };
      }
    };

    return comboBoxEditor;
  }

  @Override
  protected void configureEditor() {
    super.configureEditor();

    installEditorKeyListener(comboBox.getEditor());
    if (editor instanceof JComponent jEditor) {
      jEditor.setBorder(DEFAULT_EDITOR_BORDER);

      editorHoverListener = new MouseHoverPropertyTrigger(comboBox, HOVER_PROPERTY);

      if (editor instanceof JTextComponent) {
        editor.addMouseListener(editorHoverListener);
      }
      else {
        EditorTextField etf = UIUtil.findComponentOfType((JComponent)editor, EditorTextField.class);
        if (etf != null) {
          etf.addMouseListener(editorHoverListener);
          etf.setBackground(getComboBackground(true));
        }
      }
    }
  }

  @Override
  protected void unconfigureEditor() {
    super.unconfigureEditor();

    if (editor instanceof JTextComponent) {
      if (editorHoverListener != null) {
        editor.removeMouseListener(editorHoverListener);
      }
    }
    else {
      EditorTextField etf = UIUtil.findComponentOfType((JComponent)editor, EditorTextField.class);
      if (etf != null) {
        if (editorHoverListener != null) {
          etf.removeMouseListener(editorHoverListener);
        }
      }
    }
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    if (comboBox == null || arrowButton == null) {
      return; //NPE on LaF change
    }

    Graphics2D g2 = (Graphics2D)g.create();

    try {
      checkFocus();

      Rectangle r = new Rectangle(x, y, width, height);
      boolean isCellRenderer = DarculaUIUtil.isTableCellEditor(c);
      int bw = 1;

      if (comboBox.isEnabled()) {
        Outline op = DarculaUIUtil.getOutline(comboBox);
        if (op != null) {
          op.setGraphicsColor(g2, hasFocus);
          bw = isCellRenderer ? 1 : 2;
        }
        else if (comboBox.isEditable()) {
          if (hasFocus) {
            g2.setColor(UIManager.getColor("TextField.focusedBorderColor"));
          }
          else {
            g2.setColor(UIManager.getColor(isEditorHover() ? "TextField.hoverBorderColor" : "TextField.borderColor"));
          }
        }
        else {
          if (isPressed() || popup.isVisible()) {
            g2.setColor(UIManager.getColor("Button.intellij.native.pressedBorderColor"));
          }
          else if (isHover() || hasFocus) {
            g2.setColor(UIManager.getColor("Button.intellij.native.focusedBorderColor"));
          }
          else {
            g2.setColor(UIManager.getColor("Button.intellij.native.borderColor"));
          }
        }

        if (!isCellRenderer) {
          JBInsets.removeFrom(r, JBUI.insets(1));
        }
      }
      else {
        g2.setColor(UIManager.getColor("Button.intellij.native.borderColor"));

        float alpha = comboBox.isEditable() ? 0.35f : 0.47f;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        JBInsets.removeFrom(r, JBUI.insets(1));
      }

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(r, false);

      Rectangle innerRect = new Rectangle(r);
      JBInsets.removeFrom(innerRect, JBUI.insets(bw));
      border.append(innerRect, false);

      g2.fill(border);
    }
    finally {
      g2.dispose();
    }
  }

  private boolean isHover() {
    return comboBox != null && comboBox.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE ||
           arrowButton != null && arrowButton.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE;
  }

  private boolean isEditorHover() {
    JComponent ec = (JComponent)comboBox.getEditor().getEditorComponent();
    EditorTextField etf = UIUtil.findComponentOfType((JComponent)editor, EditorTextField.class);
    Editor editor = etf != null ? etf.getEditor() : null;

    return arrowButton != null && arrowButton.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE ||
           ec != null && ec.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE ||
           editor != null && editor.getContentComponent().getClientProperty(HOVER_PROPERTY) == Boolean.TRUE;
  }

  private boolean isPressed() {
    return comboBox != null && comboBox.getClientProperty(PRESSED_PROPERTY) == Boolean.TRUE ||
           arrowButton != null && arrowButton.getModel().isPressed();
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(DarculaUIUtil.isTableCellEditor(c) ? 0 : 1).asUIResource();
  }

  @Override
  protected Dimension getSizeWithButton(Dimension size, Dimension editorSize) {
    ARROW_BUTTON_SIZE.update();

    Insets i = getInsets();
    int editorHeight = editorSize != null ? editorSize.height + i.top + i.bottom + padding.top + padding.bottom : 0;
    int editorWidth = editorSize != null ? editorSize.width + i.left + padding.left + padding.right : 0;
    editorWidth = Math.max(editorWidth, DarculaUIUtil.MINIMUM_WIDTH.get() + i.left);

    int width = size != null ? size.width : 0;
    int height = size != null ? size.height : 0;

    width = Math.max(editorWidth + ARROW_BUTTON_SIZE.width, width + padding.left);
    height = Math.max(ARROW_BUTTON_SIZE.height, Math.max(editorHeight, height));

    return new Dimension(width, height);
  }

  @Override
  protected LayoutManager createLayoutManager() {
    return new BasicComboBoxUI.ComboBoxLayoutManager() {
      @Override
      public void layoutContainer(Container parent) {
        JComboBox cb = (JComboBox)parent;

        if (arrowButton != null) {
          if (cb.getComponentOrientation().isLeftToRight()) {
            arrowButton.setBounds(cb.getWidth() - ARROW_BUTTON_SIZE.width, 0, ARROW_BUTTON_SIZE.width, cb.getHeight());
          }
          else {
            arrowButton.setBounds(0, 0, ARROW_BUTTON_SIZE.width, cb.getHeight());
          }
        }
        layoutEditor();
      }
    };
  }

  private class ComboBoxMouseListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      setPressedProperty(true);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      setPressedProperty(false);
    }

    private void setPressedProperty(boolean isPressed) {
      if (!comboBox.isEditable()) {
        comboBox.putClientProperty(PRESSED_PROPERTY, Boolean.valueOf(isPressed));
        comboBox.repaint();
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      setHoverProperty(true);
    }

    @Override
    public void mouseExited(MouseEvent e) {
      setHoverProperty(false);
    }

    private void setHoverProperty(boolean isHover) {
      comboBox.putClientProperty(HOVER_PROPERTY, Boolean.valueOf(isHover));
      comboBox.repaint();
    }
  }


  @Override
  protected ComboPopup createPopup() {
    if (comboBox.getClientProperty(DarculaJBPopupComboPopup.CLIENT_PROP) != null) {
      return new DarculaJBPopupComboPopup<>(comboBox) {
        @Override
        public void configureList(@NotNull JList<Object> list) {
          super.configureList(list);
          list.setBackground(UIManager.getColor("TextField.background"));
        }

        @Override
        public void customizeListRendererComponent(JComponent component) {
          super.customizeListRendererComponent(component);
          component.setBorder(getList().getComponentOrientation().isLeftToRight()
                              ? JBUI.Borders.empty(0, 5, 0, 1)
                              : JBUI.Borders.empty(0, 1, 0, 5));
        }
      };
    }
    return new CustomComboPopup(comboBox) {
      @Override
      protected void configurePopup() {
        super.configurePopup();
        addPopupMenuListener(new PopupMenuListenerAdapter() {
          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            comboBox.repaint();
          }
        });
        setBorder(UIManager.getBorder("PopupMenu.border"));
      }

      @Override
      protected void configureList() {
        super.configureList();
        list.setBackground(UIManager.getColor("TextField.background"));
      }

      @Override
      protected void customizeListRendererComponent(JComponent component) {
        super.customizeListRendererComponent(component);
        component.setBorder(list.getComponentOrientation().isLeftToRight()
                            ? JBUI.Borders.empty(0, 5, 0, 1)
                            : JBUI.Borders.empty(0, 1, 0, 5));
      }

      @Override
      public void show(Component invoker, int x, int y) {
        int i = DarculaUIUtil.isTableCellEditor(comboBox) ? 0 : 1;
        int yOffset = JBUIScale.scale(i);
        super.show(invoker, x, y - yOffset);
      }
    };
  }
}
