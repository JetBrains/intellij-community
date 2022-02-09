package com.intellij.laf.win10;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ui.ComboBoxCompositeEditor;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

import static com.intellij.laf.win10.WinIntelliJTextFieldUI.HOVER_PROPERTY;

class WinIntelliJEditorTextFieldBorder extends DarculaEditorTextFieldBorder {
  WinIntelliJEditorTextFieldBorder(EditorTextField editorTextField, EditorEx editor) {
    super(editorTextField, editor);
    editor.addEditorMouseListener(new EditorMouseListener() {
      @Override
      public void mouseEntered(@NotNull EditorMouseEvent e) {
        editorTextField.putClientProperty(HOVER_PROPERTY, Boolean.TRUE);
        editorTextField.repaint();
      }

      @Override
      public void mouseExited(@NotNull EditorMouseEvent e) {
        editorTextField.putClientProperty(HOVER_PROPERTY, Boolean.FALSE);
        editorTextField.repaint();
      }
    });
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

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      Rectangle r = new Rectangle(x, y, width, height);
      boolean isCellRenderer = DarculaUIUtil.isTableCellEditor(c);

      if (ComponentUtil.getParentOfType((Class<? extends Wrapper>)Wrapper.class, c) != null && TextFieldWithPopupHandlerUI
        .isSearchFieldWithHistoryPopup(c)) {
        JBInsets.removeFrom(r, JBInsets.create(2, 0));
      }

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      // Fill background area of border
      if (isBorderOpaque() || c.getParent() != null) {
        g2.setColor(c.getParent().getBackground());

        Path2D borderArea = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        borderArea.append(r, false);

        Rectangle innerRect = new Rectangle(r);
        JBInsets.removeFrom(innerRect, JBUI.insets(isCellRenderer ? 1 : 2));
        borderArea.append(innerRect, false);
        g2.fill(borderArea);
      }

      // draw border itself
      boolean hasFocus = editorTextField.getFocusTarget().hasFocus();
      int bw = 1;

      Object op = editorTextField.getClientProperty("JComponent.outline");
      if (editorTextField.isEnabled() && op != null) {
        DarculaUIUtil.Outline.valueOf(op.toString()).setGraphicsColor(g2, c.hasFocus());
        bw = isCellRenderer ? 1 : 2;
      }
      else {
        if (hasFocus) {
          g2.setColor(UIManager.getColor("TextField.focusedBorderColor"));
        }
        else if (editorTextField.isEnabled() &&
                 editorTextField.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE) {
          g2.setColor(UIManager.getColor("TextField.hoverBorderColor"));
        }
        else {
          g2.setColor(UIManager.getColor("TextField.borderColor"));
        }

        if (!isCellRenderer) {
          JBInsets.removeFrom(r, JBUI.insets(1));
        }
      }

      if (!editorTextField.isEnabled()) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.47f));
      }

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

  @Override
  public Insets getBorderInsets(Component c) {
    if (ComponentUtil.getParentOfType((Class<? extends ComboBoxCompositeEditor>)ComboBoxCompositeEditor.class, c) != null) {
      return JBInsets.emptyInsets().asUIResource();
    }
    return (DarculaUIUtil.isTableCellEditor(c) ?
            JBUI.insets(1) :
            isComboBoxEditor(c) ? JBInsets.create(1, 6) : JBInsets.create(4, 6)).asUIResource();
  }

  @Nullable
  @Override
  public Insets getVisualPaddings(@NotNull Component component) {
    return JBUI.insets(1);
  }
}
