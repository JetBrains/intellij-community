package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.ProcessInfo;
import com.intellij.util.ui.InplaceButton;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;

import javax.swing.*;
import java.awt.*;

public class InlineProgressIndicator extends ProgressIndicatorBase {

  JLabel myText = new JLabel();
  JLabel myText2 = new JLabel();

  JProgressBar myProgress = new JProgressBar(JProgressBar.HORIZONTAL, 0, 100);

  private Dimension myPreferredSize;

  JPanel myComponent = new JPanel() {
    public Dimension getPreferredSize() {
      final Dimension size = super.getPreferredSize();
      if (myPreferredSize != null) {
        size.height = myPreferredSize.height;
      }
      return size;
    }
  };

  InplaceButton myCancelButton;

  private boolean myCompact;

  private MergingUpdateQueue myQueue = new MergingUpdateQueue("InlineProcessIndicator", 100, false, myComponent);

  public InlineProgressIndicator(boolean compact, ProcessInfo processInfo) {
    myCompact = compact;

    myCancelButton = new InplaceButton(IconLoader.getIcon("/actions/cleanLight.png"), IconLoader.getIcon("/actions/clean.png")) {
      protected void execute() {
        cancelRequest();
      }
    };
    myCancelButton.setToolTipText(processInfo.getCancelTooltip());

    if (myCompact) {
      myComponent.setLayout(new BorderLayout(0, 0));
      final JPanel textAndProgress = new JPanel(new BorderLayout());
      myText.setHorizontalAlignment(JLabel.RIGHT);
      textAndProgress.add(myText, BorderLayout.CENTER);
      textAndProgress.add(myProgress, BorderLayout.EAST);
      myComponent.add(textAndProgress, BorderLayout.CENTER);
      myComponent.add(myCancelButton, BorderLayout.EAST);
    }
    else {
      myComponent.setLayout(new BorderLayout());
      myComponent.add(myCancelButton, BorderLayout.EAST);
      myComponent.add(myText, BorderLayout.NORTH);
      myComponent.add(myProgress, BorderLayout.CENTER);
      myComponent.add(myText2, BorderLayout.SOUTH);
    }

    if (!myCompact) {
      computePreferredHeight();
    }

    new UiNotifyConnector(myComponent, myQueue);
  }

  protected void cancelRequest() {
    cancel();
  }

  private void computePreferredHeight() {
    UIUtil.removeQuaquaVisualMarginsIn(myComponent);

    setText("XXX");
    setText2("XXX");
    setFraction(0.5);
    myComponent.invalidate();

    myPreferredSize = myComponent.getPreferredSize();

    setText(null);
    setText2(null);
    setFraction(0);
  }

  public void setText(String text) {
    super.setText(text);
  }

  protected void queueUpdate() {
    myQueue.queue(new Update(this) {
      public void run() {
        updateComponentVisibility(myProgress, getFraction() > 0 || isIndeterminate());
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

        myText.setText(getText());
        myText2.setText(getText2());

        myCancelButton.setActive(isCancelable());

        myComponent.repaint();
      }
    });
  }

  private static void updateComponentVisibility(JComponent component, boolean holdsValue) {
    if (holdsValue && !component.isVisible()) {
      component.setVisible(true);
    }
    else if (!holdsValue && component.getParent() != null) {
      component.setVisible(false);
    }
  }

  protected void onStateChange() {
    queueUpdate();
  }


  protected void onFinished() {
    myQueue.dispose();
  }

  public JComponent getComponent() {
    return myComponent;
  }

}
