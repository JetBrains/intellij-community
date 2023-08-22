package com.intellij.laf.macos;

import com.intellij.ide.ui.laf.PluggableLafInfo;
import com.intellij.ide.ui.laf.SearchTextAreaPainter;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;
import java.awt.*;

final class MacSearchPainter implements SearchTextAreaPainter {
  private final PluggableLafInfo.SearchAreaContext myContext;

  MacSearchPainter(PluggableLafInfo.SearchAreaContext context) {
    myContext = context;
  }

  @Override
  public @NotNull Border getBorder() {
    return JBUI.Borders.empty(3 + Math.max(0, JBUIScale.scale(16) - UIUtil.getLineHeight(myContext.getTextComponent())) / 2, 6, 4, 4);
  }

  @Override
  public @NotNull String getLayoutConstraints() {
    return "flowx, ins 0, gapx " + JBUIScale.scale(4);
  }

  @Override
  public @NotNull String getHistoryButtonConstraints() {
    int extraGap = getExtraGap();
    return "ay top, gaptop " + extraGap + ", gapleft" + (JBUIScale.isUsrHiDPI() ? 4 : 0);
  }

  private int getExtraGap() {
    int height = UIUtil.getLineHeight(myContext.getTextComponent());
    Insets insets = myContext.getTextComponent().getInsets();
    return Math.max(JBUIScale.isUsrHiDPI() ? 0 : 1, (height + insets.top + insets.bottom - JBUIScale.scale(16)) / 2);
  }


  @Override
  public @NotNull String getIconsPanelConstraints() {
    int extraGap = getExtraGap();
    return "gaptop " + extraGap + ", ay top, gapright " + extraGap / 2;
  }

  @Override
  public @NotNull Border getIconsPanelBorder(int rows) {
    return JBUI.Borders.emptyBottom(rows == 2 ? 3 : 0);
  }

  @Override
  public void paint(@NotNull Graphics2D g) {
    JComponent c = myContext.getSearchComponent();
    JComponent iconsPanel = myContext.getIconsPanel();
    JComponent scrollPane = myContext.getScrollPane();
    JTextComponent textComponent = myContext.getTextComponent();

    Rectangle r = new Rectangle(c.getSize());
    int h = iconsPanel.getParent() != null ? Math.max(iconsPanel.getHeight(), scrollPane.getHeight()) : scrollPane.getHeight();

    Insets i = c.getInsets();
    Insets ei = textComponent.getInsets();

    int deltaY = i.top - ei.top;
    r.y += deltaY;
    r.height = Math.max(r.height, h + i.top + i.bottom) - (i.bottom - ei.bottom) - deltaY;
    MacIntelliJTextBorder.paintMacSearchArea(g, r, textComponent, true);
  }
}
