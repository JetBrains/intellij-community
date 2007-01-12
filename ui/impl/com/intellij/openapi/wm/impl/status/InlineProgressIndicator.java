package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.ProcessInfo;
import com.intellij.util.ui.InplaceButton;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public class InlineProgressIndicator extends ProgressIndicatorBase {

  FixedHeightLabel myText = new FixedHeightLabel();
  FixedHeightLabel myText2 = new FixedHeightLabel();

  MyProgressBar myProgress = new MyProgressBar(JProgressBar.HORIZONTAL);

  JPanel myComponent = new JPanel();

  InplaceButton myCancelButton;

  private boolean myCompact;

  public InlineProgressIndicator(boolean compact, ProcessInfo processInfo) {
    myCompact = compact;

    myCancelButton = new InplaceButton(IconLoader.getIcon("/actions/cleanLight.png"), IconLoader.getIcon("/actions/clean.png")) {
      protected void execute() {
        cancelRequest();
      }
    };
    myCancelButton.setOpaque(true);
    myCancelButton.setToolTipText(processInfo.getCancelTooltip());

    if (myCompact) {
      myComponent.setLayout(new BorderLayout(2, 0));
      final JPanel textAndProgress = new JPanel(new BorderLayout());
      myText.setHorizontalAlignment(JLabel.RIGHT);
      textAndProgress.add(myText, BorderLayout.CENTER);
      textAndProgress.add(myProgress, BorderLayout.EAST);
      myComponent.add(textAndProgress, BorderLayout.CENTER);
      myComponent.add(myCancelButton, BorderLayout.EAST);
      myComponent.setToolTipText(processInfo.getProcessTitle() + ". " + IdeBundle.message("progress.text.clickToViewProgressWindow"));
      myProgress.setActive(false);
    } else {
      myComponent.setLayout(new BorderLayout());
      myCancelButton.setBorder(new EmptyBorder(0, 2, 0, 0));
      myComponent.add(myCancelButton, BorderLayout.EAST);
      myComponent.add(myText, BorderLayout.NORTH);
      myComponent.add(myProgress, BorderLayout.CENTER);
      myComponent.add(myText2, BorderLayout.SOUTH);
    }

    UIUtil.removeQuaquaVisualMarginsIn(myComponent);

    if (!myCompact) {
      myText.recomputeSize();
      myText2.recomputeSize();
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

    myCancelButton.setActive(isCancelable());
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
      bar.repaint();
      myComponent.revalidate();
      myComponent.repaint();
    }
    else if (!holdsValue && bar.isActive()) {
      bar.setActive(false);
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

}
