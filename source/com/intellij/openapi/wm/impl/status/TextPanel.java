package com.intellij.openapi.wm.impl.status;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.SplittingUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class TextPanel extends JPanel {
  private String myText;
  private Font STATUS_FONT;
  private boolean myEnabled = true;
  private final String[] myPossibleStrings;
  private final boolean myShouldShowTooltip;

  public TextPanel(final String[] possibleStrings, final boolean shouldShowTooltip) {
    myText = "";
    myPossibleStrings = possibleStrings;
    myShouldShowTooltip = shouldShowTooltip;

    if (myShouldShowTooltip) {
      addMouseListener(new MyMouseListener());
    }
  }

  private String splitText(final JLabel label, final String text, final int widthLimit){
    final FontMetrics fontMetrics = label.getFontMetrics(label.getFont());

    final String[] lines = SplittingUtil.splitText(text, fontMetrics, widthLimit, ' ');

    final StringBuffer result = new StringBuffer();
    for (int i = 0; i < lines.length; i++) {
      final String line = lines[i];
      if (i > 0) {
        result.append('\n');
      }
      result.append(line);
    }
    return result.toString();
  }


  public final void updateUI() {
    super.updateUI();
    final Font font = UIManager.getFont("Label.font");
    if (font!=null){
      STATUS_FONT = font;//.deriveFont(Font.BOLD, font.getSize());
      setFont(STATUS_FONT);
    }
  }

  public final void setText(final String text) {
    myText = text;
    repaint();
  }

  public final void setEnabled(final boolean enabled) {
    myEnabled = enabled;
  }

  public final void paintComponent(final Graphics g) {
    super.paintComponent(g);
    g.setColor(
      myEnabled ? UIManager.getColor("Label.foreground") : getBackground().darker()/*UIManager.getColor("Label.disabledForeground")*/
    );
    g.setFont(STATUS_FONT);
    g.drawString(myText, 10, getLineHeight()+(getSize().height-getLineHeight())/2);
  }

  public final Dimension getPreferredSize(){
    int max = 0;
    for (int i = 0; i < myPossibleStrings.length; i++) {
      max = Math.max(max, getLineWidth(myPossibleStrings[i]));
    }
    return new Dimension(20 + max, getLineHeight() + 6);
  }

  private int getLineHeight() {
    return getFontMetrics(STATUS_FONT).getAscent();
  }

  private int getLineWidth(final String longestString) {
    return getFontMetrics(STATUS_FONT).stringWidth(longestString);
  }

  private final class MyMouseListener extends MouseAdapter {
    private LightweightHint myHint;

    public void mouseEntered(final MouseEvent e){
      if (myHint != null) {
        myHint.hide();
        myHint = null;
      }

      final int widthLimit = getSize().width - 20;

      if (getFontMetrics(STATUS_FONT).stringWidth(myText) < widthLimit) return;

      final JLabel label = new JLabel();
      label.setBorder(
        BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(Color.black),
          BorderFactory.createEmptyBorder(0,5,0,5)
        )
      );
      label.setForeground(Color.black);
      label.setBackground(HintUtil.INFORMATION_COLOR);
      label.setOpaque(true);
      label.setUI(new MultiLineLabelUI());

      final JLayeredPane layeredPane = getRootPane().getLayeredPane();


      label.setText(splitText(label, myText, layeredPane.getWidth() - 10));

      final Point p = SwingUtilities.convertPoint(TextPanel.this, 1, getHeight() - 1, layeredPane);
      p.y -= label.getPreferredSize().height;

      myHint = new LightweightHint(label);
      myHint.show(layeredPane, p.x, p.y, null);
    }

    public void mouseExited(final MouseEvent e){
      if (myHint != null) {
        myHint.hide();
        myHint = null;
      }
    }
  }
}
