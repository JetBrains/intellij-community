package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.ui.UIBundle;

import javax.swing.*;
import java.awt.*;

import org.jetbrains.annotations.NonNls;

//Made public for Fabrique
public class MemoryUsagePanel extends JPanel {
  private static final int MEGABYTE = 1024 * 1024;
  private static final Color ourColorFree = new Color(240, 240, 240);
  private static final Color ourColorUsed = new Color(112, 135, 214);
  private static final Color ourColorUsed2 = new Color(166, 181, 230);
  private static final Icon ourRunGCButtonIcon = IconLoader.getIcon("/actions/gc.png");

  private final JPanel myIndicatorPanel;
  private final Alarm myAlarm;

  public MemoryUsagePanel() {
    setLayout(new BorderLayout());
    setOpaque(false);
    myIndicatorPanel = new IndicatorPanel();
    myIndicatorPanel.setBorder(BorderFactory.createEmptyBorder(0, 1, 1, 1));

    myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

    add(myIndicatorPanel, BorderLayout.CENTER);

    final RunCGAction gcAction = new RunCGAction();
    final JComponent button = gcAction.createButton(gcAction.getTemplatePresentation());
    add(button, SystemInfo.isMac ? BorderLayout.WEST : BorderLayout.EAST);

    updateUI();
  }

  /**
   * Invoked when enclosed frame is being shown.
   */
  public void addNotify() {
    final Runnable runnable = new Runnable() {
      public void run() {
        SwingUtilities.invokeLater(
          new Runnable() {
            public void run() {
              if (!isDisplayable()) return; // This runnable may be posted in event queue while calling removeNotify.
              try {
                updateState();
              }
              finally {
                myAlarm.addRequest(this, 1000);
              }
            }
          }
        );
      }
    };
    myAlarm.addRequest(runnable, 1000);
    super.addNotify();
  }

  private void updateState() {
    if (!isShowing()) {
      return;
    }
    final Runtime runtime = Runtime.getRuntime();
    repaint();
    final long total = runtime.totalMemory();
    final long used = total - runtime.freeMemory();
    myIndicatorPanel.setToolTipText(UIBundle.message("memory.usage.panel.statistics.message", total / MEGABYTE, used / MEGABYTE));
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
  public void removeNotify() {
    myAlarm.cancelAllRequests();
    super.removeNotify();
  }

  private final class RunCGAction extends AnAction {
    public RunCGAction() {
      super(UIBundle.message("memory.usage.panel.run.garbage.collector.action.name"),
            UIBundle.message("memory.usage.panel.run.garbage.collector.action.description"), ourRunGCButtonIcon);
    }

    public JComponent createButton(final Presentation presentation) {
      final ActionButton button = new ActionButton(
        this,
        presentation,
        ActionPlaces.UNKNOWN,
        new Dimension(1, 1)
      );
      presentation.putClientProperty("button", button);
      return button;
    }

    public void actionPerformed(final AnActionEvent e) {
      System.gc();
      updateState();
    }
  }

  private final class IndicatorPanel extends JPanel {
    @NonNls protected static final String SAMPLE_STRING = "0000M of 0000M";

    public IndicatorPanel() {
      updateUI();
    }

    public final void updateUI() {
      super.updateUI();
      setPreferredSize(new Dimension(getPreferedWidth(), getLineHeight()));
    }

    public final void paint(final Graphics g) {
      super.paint(g);
      final Dimension size = getSize();
      final Insets insets = getInsets();

      final int x = insets.left;
      final int y = insets.top;

      final Runtime runtime = Runtime.getRuntime();
      final long freeMemory = runtime.freeMemory();
      final long totalMemory = runtime.totalMemory();

      final int totalBarLength = size.width - (insets.left + insets.right);
      final int usedBarLength = totalBarLength - (int)(totalBarLength * freeMemory / totalMemory);
      final int barHeight = size.height - (insets.bottom + insets.top);
      final Graphics2D g2 = (Graphics2D)g;
      g2.setPaint(
        new GradientPaint(x, y, ourColorUsed, x, barHeight / 2, ourColorUsed2, true)
      );
      g.fillRect(x, y, usedBarLength, barHeight);
      g.setColor(ourColorFree);
      g.fillRect(x + usedBarLength, y, totalBarLength - usedBarLength, barHeight);

      g.setFont(UIUtil.getLabelFont());
      g.setColor(Color.black);
      final long used = (totalMemory - freeMemory) / MEGABYTE;
      final long total = totalMemory / MEGABYTE;
      final String info = UIBundle.message("memory.usage.panel.message.text", Long.toString(used), Long.toString(total));
      final FontMetrics fontMetrics = g.getFontMetrics();
      final int infoWidth = fontMetrics.charsWidth(info.toCharArray(), 0, info.length());
      final int infoHeight = fontMetrics.getHeight() - fontMetrics.getDescent();
      g.drawString(info, x + (totalBarLength - infoWidth) / 2, y + (barHeight + infoHeight) / 2);
    }

    public final int getLineHeight() {
      final FontMetrics fontMetrics = getFontMetrics(UIUtil.getLabelFont());
      return fontMetrics.getHeight() - fontMetrics.getDescent();
    }

    public final int getPreferedWidth() {
      return getFontMetrics(UIUtil.getLabelFont()).stringWidth(SAMPLE_STRING);
    }
  }
}
