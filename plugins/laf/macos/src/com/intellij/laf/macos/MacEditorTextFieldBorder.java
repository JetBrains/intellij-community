package com.intellij.laf.macos;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.Gray;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

final class MacEditorTextFieldBorder extends DarculaEditorTextFieldBorder {
  MacEditorTextFieldBorder(EditorTextField editorTextField, EditorEx editor) {
    super(editorTextField, editor);
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    if (isComboBoxEditor(c)) {
      g.setColor(c.getBackground());
      g.fillRect(x, y, width, height);
      return;
    }

    EditorTextField editorTextField = ComponentUtil.getParentOfType((Class<? extends EditorTextField>)EditorTextField.class, c);
    if (editorTextField == null) return;
    boolean hasFocus = editorTextField.getFocusTarget().hasFocus();

    if (DarculaUIUtil.isTableCellEditor(c)) {
      DarculaUIUtil.paintCellEditorBorder((Graphics2D)g, c, new Rectangle(x, y, width, height), hasFocus);
    }
    else {
      Graphics2D g2 = (Graphics2D)g.create();
      try {
        if (c.isOpaque() || c instanceof JComponent && ((JComponent)c).getClientProperty(MacUIUtil.MAC_FILL_BORDER) == Boolean.TRUE) {
          g2.setColor(UIUtil.getPanelBackground());
          g2.fillRect(x, y, width, height);
        }

        Rectangle2D rect = new Rectangle2D.Float(
          x + JBUIScale.scale(3), y + JBUIScale.scale(3), width - JBUIScale.scale(3) * 2, height - JBUIScale.scale(3) * 2);
        g2.setColor(c.getBackground());
        g2.fill(rect);

        if (!editorTextField.isEnabled()) {
          g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        }

        float bw = UIUtil.isRetina(g2) ? 0.5f : 1.0f;
        Path2D outline = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        outline.append(rect, false);
        outline.append(new Rectangle2D.Float((float)rect.getX() + bw,
                                             (float)rect.getY() + bw,
                                             (float)rect.getWidth() - 2 * bw,
                                             (float)rect.getHeight() - 2 * bw), false);
        g2.setColor(Gray.xBC);
        g2.fill(outline);

        g2.translate(x, y);

        Object op = editorTextField.getClientProperty("JComponent.outline");
        if (editorTextField.isEnabled() && op != null) {
          DarculaUIUtil.paintOutlineBorder(g2, width, height, 0, true, hasFocus, DarculaUIUtil.Outline.valueOf(op.toString()));
        }
        else if (editorTextField.isEnabled() && editorTextField.isVisible() && hasFocus) {
          DarculaUIUtil.paintFocusBorder(g2, width, height, 0, true);
        }
      }
      finally {
        g2.dispose();
      }
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return DarculaUIUtil.isTableCellEditor(c) || DarculaUIUtil.isCompact(c) || isComboBoxEditor(c) ?
           JBInsets.create(2, 3).asUIResource() : JBInsets.create(5, 8).asUIResource();
  }
}