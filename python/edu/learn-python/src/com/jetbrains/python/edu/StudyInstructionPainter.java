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

    painter.appendLine("PyCharm Educational Edition").underlined(new JBColor(Gray._150, Gray._180));
    painter.appendLine("Navigate to the next task window with Ctrl + Enter").smaller().withBullet();
    painter.appendLine("Navigate between task windows with Ctrl + < and Ctrl + >").smaller().withBullet();
    painter.appendLine("Get hint for the task window using Ctrl + 7").smaller().withBullet();
    painter.appendLine("To see your progress open the 'Course Description' panel").smaller().withBullet();
                       painter.draw(g, new PairFunction<Integer, Integer, Couple<Integer>>() {
                         @Override
                         public Couple<Integer> fun(Integer width, Integer height) {
                           Dimension s = splitters.getSize();
                           return Couple.of((s.width - width) / 2, (s.height - height) / 2);
                         }
                       });
  }
}
