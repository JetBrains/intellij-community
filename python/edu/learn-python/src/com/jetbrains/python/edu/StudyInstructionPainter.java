package com.jetbrains.python.edu;

import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.util.Couple;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.PairFunction;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.edu.ui.StudyCondition;

import java.awt.*;

/**
 * author: liana
 * data: 7/29/14.
 */
public class StudyInstructionPainter extends EditorEmptyTextPainter {
  @Override
  public void paintEmptyText(final EditorsSplitters splitters, Graphics g) {
    if (!StudyCondition.VALUE) {
      super.paintEmptyText(splitters, g);
      return;
    }
    boolean isDarkBackground = UIUtil.isUnderDarcula();
    UIUtil.applyRenderingHints(g);
    GraphicsUtil.setupAntialiasing(g, true, false);
    g.setColor(new JBColor(isDarkBackground ? Gray._230 : Gray._80, Gray._160));
    g.setFont(UIUtil.getLabelFont().deriveFont(isDarkBackground ? 24f : 20f));

    UIUtil.TextPainter painter = new UIUtil.TextPainter().withLineSpacing(1.5f);
    painter.withShadow(true, new JBColor(Gray._200.withAlpha(100), Gray._0.withAlpha(255)));

    painter.appendLine("PyCharm Educational Edition").underlined(new JBColor(Gray._150, Gray._180));
    painter.appendLine("Learn Python programming in your favorite IDE").smaller();
    painter.appendLine("Navigate between tasks with Ctrl + Shift + < and Ctrl + Shift + >").smaller().withBullet();
    painter.appendLine("Navigate to the next task window with Ctrl + Enter").smaller().withBullet();
    painter.appendLine("Navigate between task windows with Ctrl + < and Ctrl + >").smaller().withBullet();
    painter.draw(g, new PairFunction<Integer, Integer, Couple<Integer>>() {
      @Override
      public Couple<Integer> fun(Integer width, Integer height) {
        Dimension s = splitters.getSize();
        return Couple.of((s.width - width) / 2, (s.height - height) / 2);
      }
    });
  }
}
