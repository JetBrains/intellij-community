package com.intellij.openapi.diff.ex;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.vcs.checkin.DifferenceType;
import com.intellij.openapi.vcs.checkin.ex.DifferenceTypeEx;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Yura Cangea
 */
public class DiffStatusBar extends JPanel {
  public static final List<LegendTypeDescriptor> DEFAULT_TYPES =
    Arrays.asList(
      new LegendTypeDescriptor[]{
        (DifferenceTypeEx)DifferenceType.MODIFIED,
        (DifferenceTypeEx)DifferenceType.INSERTED,
        (DifferenceTypeEx)DifferenceType.DELETED});

  private final Collection<JComponent> myLabels = new ArrayList<JComponent>();

  private final JLabel myTextLabel = new JLabel("", JLabel.CENTER);
  private static final int COMP_HEIGHT = 40;
  private EditorColorsScheme myColorScheme = null;

  public <T extends LegendTypeDescriptor> DiffStatusBar(List<T> types) {
    for (T differenceType : types) {
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
    Box box = Box.createHorizontalBox();
    box.add(Box.createHorizontalGlue());
    JPanel panel = new JPanel(new GridLayout(1, myLabels.size(), 0, 0));
    for (final JComponent myLabel : myLabels) {
      panel.add(myLabel);
    }
    box.add(panel);
    box.add(Box.createHorizontalGlue());

    add(box, BorderLayout.CENTER);
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
