package com.intellij.openapi.diff.ex;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.vcs.checkin.DifferenceType;
import com.intellij.openapi.vcs.checkin.ex.DifferenceTypeEx;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author Yura Cangea
 */
public class DiffStatusBar extends JPanel {
  public static final java.util.List<LegendTypeDescriptor> DEFAULT_TYPES =
    Arrays.asList(
      new LegendTypeDescriptor[]{
        (DifferenceTypeEx)DifferenceType.MODIFIED,
        (DifferenceTypeEx)DifferenceType.INSERTED,
        (DifferenceTypeEx)DifferenceType.DELETED});

  private final Collection myLabels = new ArrayList();

  private final JLabel myTextLabel = new JLabel("", JLabel.CENTER);
  private static final int COMP_HEIGHT = 40;
  private EditorColorsScheme myColorScheme = null;

  public <T extends LegendTypeDescriptor> DiffStatusBar(java.util.List<T> types) {
    for (Iterator<T> iterator = types.iterator(); iterator.hasNext();) {
      LegendTypeDescriptor differenceType = iterator.next();
      addDiffType(differenceType);
    }
    initGui();
    setBorder(BorderFactory.createLineBorder(Color.GRAY));
  }

  private void addDiffType(final LegendTypeDescriptor diffType){
    addComponent(diffType);
  }

  private void addComponent(final LegendTypeDescriptor diffType) {
    JComponent component = new JPanel() {
      public void paint(Graphics g) {
        setBackground(UIUtil.getTableHeaderBackground());
        super.paint(g);
        FontMetrics metrics = getFontMetrics(getFont());

        EditorColorsScheme colorScheme = myColorScheme != null
                                         ? myColorScheme
                                         : EditorColorsManager.getInstance().getGlobalScheme();
        g.setColor(diffType.getLegendColor(colorScheme));
        g.fill3DRect(10, (getHeight() - 10) / 2, 35, 10, true);

        Font font = g.getFont();
        if (font.getStyle() != Font.PLAIN) {
          font = font.deriveFont(Font.PLAIN);
        }
        g.setFont(font);
        g.setColor(UIUtil.getLabelForeground());
        int textBaseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
        g.drawString(diffType.getDisplayName(), 67, textBaseline);
      }
    };
    component.setMinimumSize(new Dimension(0, COMP_HEIGHT));
    component.setPreferredSize(new Dimension(0, COMP_HEIGHT));
    component.setSize(new Dimension(0, COMP_HEIGHT));
    myLabels.add(component);
  }

  public Dimension getMinimumSize() {
    Dimension p = super.getPreferredSize();
    Dimension m = super.getMinimumSize();
    return new Dimension(m.width, p.height);
  }

  public Dimension getMaximumSize() {
    Dimension p = super.getPreferredSize();
    Dimension m = super.getMaximumSize();
    return new Dimension(m.width, p.height);
  }

  public void setText(String text) {
    myTextLabel.setText(text);
  }

  private void initGui() {
    setLayout(new BorderLayout());
    Border emptyBorder = BorderFactory.createEmptyBorder(3, 2, 5, 2);
    setBorder(emptyBorder);

    add(myTextLabel, BorderLayout.WEST);
    JPanel panel = new JPanel(new GridLayout(1, myLabels.size(), 0, 0));
    for (Iterator each = myLabels.iterator(); each.hasNext();) {
      panel.add((JComponent) each.next());
    }

    add(panel, BorderLayout.CENTER);
  }

  public void setColorScheme(EditorColorsScheme colorScheme) {
    EditorColorsScheme oldScheme = myColorScheme;
    myColorScheme = colorScheme;
    if (oldScheme != colorScheme) repaint();
  }

  public interface LegendTypeDescriptor {
    String getDisplayName();
    Color getLegendColor(EditorColorsScheme colorScheme);
  }
}
