// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.laf.macos;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
import com.intellij.ide.ui.laf.darcula.ui.DarculaJBPopupComboPopup;
import com.intellij.openapi.util.ColoredItem;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.laf.macos.MacIntelliJTextBorder.ARC;
import static com.intellij.laf.macos.MacIntelliJTextBorder.BW;

/**
 * @author Konstantin Bulenkov
 */
public final class MacIntelliJComboBoxUI extends DarculaComboBoxUI {
  private static final Icon ICON = EmptyIcon.create(MacIconLookup.getIcon("comboRight", false, false, true, false));
  private static final Icon EDITABLE_ICON = EmptyIcon.create(MacIconLookup.getIcon("comboRight", false, false, true, true));

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJComboBoxUI();
  }

  @Override
  protected void installDarculaDefaults() {
    comboBox.setOpaque(false);
  }

  @Override
  protected void uninstallDarculaDefaults() {}

  @Override
  protected JButton createArrowButton() {
    Color bg = comboBox.getBackground();
    Color fg = comboBox.getForeground();
    JButton button = new BasicArrowButton(SwingConstants.SOUTH, bg, fg, fg, fg) {
      @Override
      public void paint(Graphics g) {
        if (!MacIntelliJLaf.Companion.isMacLaf()) return; // Paint events may still arrive after UI switch until entire UI is updated.

        Icon icon = MacIconLookup.getIcon("comboRight", false, false, comboBox.isEnabled(), comboBox.isEditable());
        if (getWidth() != icon.getIconWidth() || getHeight() != icon.getIconHeight()) {
          Image image = IconLoader.toImage(icon, null);
          StartupUiUtil.drawImage(g, image, new Rectangle(0, 0, getWidth(), getHeight()), null);
        }
        else {
          icon.paintIcon(this, g, 0, 0);
        }
      }

      @Override
      public Dimension getPreferredSize() {
        Icon icon = comboBox.isEditable() ? EDITABLE_ICON : ICON;
        return new Dimension(icon.getIconWidth(), icon.getIconHeight());
      }
    };
    button.setBorder(BorderFactory.createEmptyBorder());
    button.setOpaque(false);
    return button;
  }

  @Override
  protected Dimension getSizeWithButton(Dimension size, Dimension editorSize) {
    Insets i = comboBox.getInsets();
    Icon icon = comboBox.isEditable() ? EDITABLE_ICON : ICON;
    int iconWidth = icon.getIconWidth() + i.right;
    int iconHeight = icon.getIconHeight() + i.top + i.bottom;

    int editorHeight = editorSize != null ? editorSize.height + i.top + i.bottom + padding.top + padding.bottom : 0;
    int editorWidth = editorSize != null ? editorSize.width + i.left + padding.left + padding.right : 0;
    editorWidth = Math.max(editorWidth, DarculaUIUtil.MINIMUM_WIDTH.get() + i.left);

    int width = size != null ? size.width : 0;
    int height = size != null ? size.height : 0;

    width = Math.max(width + padding.left, editorWidth + iconWidth);
    height = Math.max(Math.max(iconHeight, height), editorHeight);

    return new Dimension(width, height);
  }

  @Override
  protected LayoutManager createLayoutManager() {
    return new BasicComboBoxUI.ComboBoxLayoutManager() {
      @Override
      public void layoutContainer(Container parent) {
        JComboBox cb = (JComboBox)parent;

        if (arrowButton != null) {
          Rectangle bounds = cb.getBounds();
          Insets cbInsets = cb.getInsets();
          Dimension prefSize = arrowButton.getPreferredSize();

          int buttonHeight = bounds.height - (cbInsets.top + cbInsets.bottom);
          double ar = (double)buttonHeight / prefSize.height;
          int buttonWidth = (int)Math.floor(prefSize.width * ar);
          int offset = (int)Math.round(ar - 1.0);

          arrowButton.setBounds(bounds.width - buttonWidth - cbInsets.right + offset, cbInsets.top, buttonWidth, buttonHeight);
        }

        layoutEditor();
      }
    };
  }

  @Override
  protected ComboPopup createPopup() {
    if (comboBox.getClientProperty(DarculaJBPopupComboPopup.CLIENT_PROP) != null) {
      return new DarculaJBPopupComboPopup<>(comboBox) {
        @Override
        public void configureList(@NotNull JList<Object> list) {
          super.configureList(list);
          list.setSelectionBackground(JBColor.lazy(() -> ColorUtil.withAlpha(UIManager.getColor("ComboBox.selectionBackground"), 0.75)));
        }
      };
    }
    return new CustomComboPopup(comboBox) {
      @Override
      protected void configureList() {
        super.configureList();
        list.setSelectionBackground(JBColor.lazy(() -> ColorUtil.withAlpha(UIManager.getColor("ComboBox.selectionBackground"), 0.75)));
      }
    };
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    Graphics2D g2 = (Graphics2D)g.create();
    Rectangle r = new Rectangle(c.getSize());

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
      g2.translate(r.x, r.y);

      Object value = comboBox.getSelectedItem();
      Color coloredItemColor = value instanceof ColoredItem ? ((ColoredItem)value).getColor() : null;

      boolean editable = comboBox.isEnabled() && editor != null && comboBox.isEditable();
      Color bg = comboBox.getBackground();

      Color background = editable ? editor.getBackground() :
                         ObjectUtils.notNull(coloredItemColor,
                                             comboBox.isBackgroundSet() && !(bg instanceof UIResource) ? bg :
                                             comboBox.isEnabled() ? UIManager.getColor("ComboBox.background") :
                                                                    UIUtil.getComboBoxDisabledBackground());

      g2.setColor(background);

      float arc = comboBox.isEditable() ? 0 : ARC.getFloat();
      float bw = BW.getFloat();

      Area bgs = new Area(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc));
      bgs.subtract(new Area(arrowButton.getBounds()));

      g2.fill(bgs);
    }
    finally {
      g2.dispose();
    }


    if (!comboBox.isEditable()) {
      listBox.setForeground(comboBox.isEnabled() ? UIManager.getColor("Label.foreground") : UIManager.getColor("Label.disabledForeground"));
      checkFocus();
      paintCurrentValue(g, rectangleForCurrentValue(), hasFocus);
    }
  }

  @Nullable
  Rectangle getArrowButtonBounds() {
    return arrowButton != null ? arrowButton.getBounds() : null;
  }
}
