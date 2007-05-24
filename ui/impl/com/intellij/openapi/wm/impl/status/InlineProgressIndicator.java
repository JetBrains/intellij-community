package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.GraphicsConfig;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class InlineProgressIndicator extends ProgressIndicatorBase {

  FixedHeightLabel myText = new FixedHeightLabel();
  FixedHeightLabel myText2 = new FixedHeightLabel();

  MyProgressBar myProgress = new MyProgressBar(JProgressBar.HORIZONTAL);

  JPanel myComponent = new MyComponent();

  InplaceButton myCancelButton;

  private boolean myCompact;
  private TaskInfo myInfo;

  private FixedHeightLabel myProcessName = new FixedHeightLabel();

  public InlineProgressIndicator(boolean compact, TaskInfo processInfo) {
    myCompact = compact;
    myInfo = processInfo;

    myCancelButton = new InplaceButton(new IconButton(processInfo.getCancelTooltipText(),
                                                      IconLoader.getIcon("/process/stop.png"),
                                                      IconLoader.getIcon("/process/stopHovered.png")) {
    }, new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        cancelRequest();
      }
    });

    myCancelButton.setVisible(myInfo.isCancellable());
    myCancelButton.setOpaque(true);
    myCancelButton.setToolTipText(processInfo.getCancelTooltipText());

    if (myCompact) {
      myComponent.setLayout(new BorderLayout(2, 0));
      final JPanel textAndProgress = new JPanel(new BorderLayout());
      myText.setHorizontalAlignment(JLabel.RIGHT);
      textAndProgress.add(myText, BorderLayout.CENTER);

      final NonOpaquePanel progressWrapper = new NonOpaquePanel(new GridBagLayout());
      final GridBagConstraints c = new GridBagConstraints();
      c.weightx = 1;
      c.weighty = 1;
      c.fill = GridBagConstraints.HORIZONTAL;
      progressWrapper.add(myProgress, c);

      textAndProgress.add(progressWrapper, BorderLayout.EAST);
      myComponent.add(textAndProgress, BorderLayout.CENTER);
      myComponent.add(myCancelButton, BorderLayout.EAST);
      myComponent.setToolTipText(processInfo.getTitle() + ". " + IdeBundle.message("progress.text.clickToViewProgressWindow"));
      myProgress.setActive(false);
    } else {
      myComponent.setLayout(new BorderLayout());
      myProcessName.setText(processInfo.getTitle());
      myComponent.add(myProcessName, BorderLayout.NORTH);
      final Font font = myProcessName.getFont();

      final boolean aqua = LafManager.getInstance().isUnderAquaLookAndFeel();

      int size = font.getSize() - (aqua ? 4 : 2);
      if (size < (aqua ? 8 : 10)) {
        size = (aqua ? 8 : 10);
      }
      myProcessName.setFont(font.deriveFont(Font.PLAIN, size));
      myProcessName.setForeground(UIManager.getColor("Panel.background").brighter().brighter());
      myProcessName.setBorder(new EmptyBorder(2, 2, 2, 2));

      final NonOpaquePanel content = new NonOpaquePanel(new BorderLayout());
      content.setBorder(new EmptyBorder(2, 2, 2, 2));
      myComponent.add(content, BorderLayout.CENTER);

      final Wrapper cancelWrapper = new Wrapper(myCancelButton);
      cancelWrapper.setOpaque(false);
      cancelWrapper.setBorder(new EmptyBorder(0, 3, 0, 2));

      content.add(cancelWrapper, BorderLayout.EAST);
      content.add(myText, BorderLayout.NORTH);
      content.add(myProgress, BorderLayout.CENTER);
      content.add(myText2, BorderLayout.SOUTH);

      myComponent.setBorder(new EmptyBorder(2, 2, 2, 2));
    }

    UIUtil.removeQuaquaVisualMarginsIn(myComponent);

    if (!myCompact) {
      myText.recomputeSize();
      myText2.recomputeSize();
      myProcessName.recomputeSize();
    }

  }

  protected void cancelRequest() {
    cancel();
  }

  public void setText(String text) {
    super.setText(text);
  }

  private void updateRunning() {
    queueRunningUpdate(new Runnable() {
      public void run() {

      }
    });
  }

  private void updateProgress() {
    queueProgressUpdate(new Runnable() {
      public void run() {
        _updateProgress();

        myComponent.repaint();
      }
    });
  }

  private void _updateProgress() {
    updateVisibility(myProgress, getFraction() > 0 && !isIndeterminate());
    if (isIndeterminate()) {
      myProgress.setIndeterminate(true);
    }
    else {
      myProgress.setMinimum(0);
      myProgress.setMaximum(100);
    }
    if (getFraction() > 0) {
      myProgress.setValue((int)(getFraction() * 99 + 1));
    }

    myText.setText(getText() != null ? getText() : "");
    myText2.setText(getText2() != null ? getText2() : "");

    myCancelButton.setPainting(isCancelable());
  }

  protected void queueProgressUpdate(Runnable update) {
    update.run();
  }

  protected void queueRunningUpdate(Runnable update) {
    update.run();
  }

  private void updateVisibility(MyProgressBar bar, boolean holdsValue) {
    if (holdsValue && !bar.isActive()) {
      bar.setActive(true);
      bar.revalidate();
      bar.repaint();
      myComponent.revalidate();
      myComponent.repaint();
    }
    else if (!holdsValue && bar.isActive()) {
      bar.setActive(false);
      bar.revalidate();
      bar.repaint();
      myComponent.revalidate();
      myComponent.repaint();
    }
  }

  protected void onProgressChange() {
    updateProgress();
  }

  protected void onRunningChange() {
    updateRunning();
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public boolean isCompact() {
    return myCompact;
  }

  public TaskInfo getInfo() {
    return myInfo;
  }

  private class FixedHeightLabel extends JLabel {
    private Dimension myPrefSize;

    public FixedHeightLabel() {
    }

    public void recomputeSize() {
      final String old = getText();
      setText("XXX");
      myPrefSize = getPreferredSize();
      setText(old);
    }


    public Dimension getPreferredSize() {
      final Dimension size = super.getPreferredSize();
      if (myPrefSize != null) {
        size.height = myPrefSize.height;
      }

      return size;
    }
  }

  private class MyProgressBar extends JProgressBar {

    private boolean myActive = true;

    public MyProgressBar(final int orient) {
      super(orient);
    }


    protected void paintComponent(final Graphics g) {
      if (!myActive) return;

      super.paintComponent(g);
    }


    public boolean isActive() {
      return myActive;
    }


    public Dimension getPreferredSize() {
      if (!myActive && myCompact) return new Dimension(0, 0);
      return super.getPreferredSize();
    }

    public void setActive(final boolean active) {
      myActive = active;
    }
  }

  private class MyComponent extends JPanel {
    protected void paintComponent(final Graphics g) {
      if (myCompact) return;

      final GraphicsConfig c = new GraphicsConfig(g);
      c.setAntialiasing(true);

      int arc = 8;

      final Color bg = getBackground().darker();
      g.setColor(bg);

      final Rectangle bounds = myProcessName.getBounds();
      final Rectangle label = SwingUtilities.convertRectangle(myProcessName.getParent(), bounds, this);


      g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);

      g.setColor(UIManager.getColor("Panel.background"));
      g.fillRoundRect(0, getHeight() / 2, getWidth() - 1, getHeight() / 2, arc, arc);
      g.fillRect(0, (int)label.getMaxY() + 1, getWidth() - 1, getHeight() / 2);

      g.setColor(bg);
      g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);

      c.restore();
    }
  }

}
