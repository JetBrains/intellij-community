package com.intellij.html.preview;

import com.intellij.psi.PsiElement;
import com.intellij.psi.css.impl.util.CssUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.ColorSampleLookupValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author spleaner
 */
public class ColorPreviewComponent extends JPanel {
  private ColorPreviewComponent(final String hexValue, final Color color) {
    super(new BorderLayout());

    setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.black), BorderFactory.createEmptyBorder(5, 5, 5, 5)));

    setBackground(color);
    setOpaque(true);

    add(new ColorComponent(color), BorderLayout.CENTER);
    if (hexValue != null) {
      final JLabel label = new JLabel('#' + hexValue);
      label.setFont(UIUtil.getToolTipFont());
      label.setForeground(Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[2] >= 0.5f ? Color.BLACK : Color.WHITE);
      add(label, BorderLayout.SOUTH);
    }
  }

  private static class ColorComponent extends JComponent {
    private Color myColor;

    public ColorComponent(final Color color) {
      myColor = color;
    }

    public void paint(final Graphics g) {
      super.paint(g);

      final Rectangle r = getBounds();
      g.setColor(myColor);
      g.fillRect(r.x, r.y, r.width, r.height);
    }

    public Dimension getPreferredSize() {
      return new Dimension(50, 15);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Nullable
  public static JComponent getPreviewComponent(@NotNull final PsiElement element) {
    if (element.getParent() instanceof XmlAttributeValue) {
      XmlAttribute attribute = (XmlAttribute)element.getParent().getParent();
      String attrName = attribute.getName();
      if ("alink".equals(attrName) || "link".equals(attrName) | "text".equals(attrName) || "vlink".equals(attrName) ||
          "bgcolor".equals(attrName) || "color".equals(attrName)) {
        String s = element.getText();
        if (s.length() > 0) {
          final String hexColor = (s.charAt(0) == '#') ? s : ColorSampleLookupValue.getHexCodeForColorName(s);
          if (hexColor != null) {
            try {
              return new ColorPreviewComponent(null, Color.decode("0x" + hexColor.substring(1)));
            }
            catch (NumberFormatException e) {
              return null;
            }
          }
        }
      }
    }
    else {
      final Color color = CssUtil.getColor(element);
      if (color != null) {
        try {
          return new ColorPreviewComponent(null, color);
        }
        catch (NumberFormatException e) {
          return null;
        }
      }
    }

    return null;
  }
}
