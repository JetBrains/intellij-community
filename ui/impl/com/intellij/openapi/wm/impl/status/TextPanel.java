package com.intellij.openapi.wm.impl.status;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.SplittingUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class TextPanel extends JLabel {
  private String myText;
  private final String[] myPossibleStrings;

  public TextPanel(final String[] possibleStrings, final boolean shouldShowTooltip) {
    myText = "";
    myPossibleStrings = possibleStrings;

    if (shouldShowTooltip) {
      addMouseListener(new MyMouseListener());
    }
  }

  private static String splitText(final JLabel label, final String text, final int widthLimit){
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



  public final void setText(final String text) {
    super.setText(text);
    myText = text;
  }

  public void setDisplayedMnemonic(int key) {
    // status bar may not have mnemonics
  }

  public void setDisplayedMnemonicIndex(int index) throws IllegalArgumentException {
    // status bar may not have mnemonics
    super.setDisplayedMnemonicIndex(-1);
  }

  public final Dimension getPreferredSize(){
    int max = 0;
    for (String possibleString : myPossibleStrings) {
      max = Math.max(max, getFontMetrics(getFont()).stringWidth(possibleString));
    }
    return new Dimension(20 + max, getMinimumSize().height);
  }

  private final class MyMouseListener extends MouseAdapter {
    private LightweightHint myHint;

    public void mouseEntered(final MouseEvent e){
      if (myHint != null) {
        myHint.hide();
        myHint = null;
      }

      final int widthLimit = getSize().width - 20;

      if (getFontMetrics(getFont()).stringWidth(myText) < widthLimit) return;

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
