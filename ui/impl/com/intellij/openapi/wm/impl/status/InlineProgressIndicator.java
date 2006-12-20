package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class InlineProgressIndicator extends ProgressIndicatorBase {

  JLabel myText = new JLabel();
  JLabel myText2 = new JLabel();

  JProgressBar myProgress = new JProgressBar(JProgressBar.HORIZONTAL, 0, 100);

  JPanel myComponent = new JPanel(new BorderLayout());
  InlineButton myCancelButton;

  private boolean myCompact;

  public InlineProgressIndicator() {
    myComponent.add(myText, BorderLayout.WEST);
    myCancelButton = new InlineButton(IconLoader.getIcon("/actions/clean.png"), IdeBundle.message("button.cancelProcess")) {
      protected void onActionPerformed(final ActionEvent e) {
        cancel();
      }
    };
    myComponent.add(myCancelButton, BorderLayout.EAST);
  }


  protected void queueUpdate() {
    boolean revalidate = updateComponent(myProgress, getFraction() > 0 || isIndeterminate(), BorderLayout.CENTER);
    if (isIndeterminate()) {
      myProgress.setIndeterminate(true);
    } else {
      myProgress.setMinimum(0);
      myProgress.setMaximum(100);
    }
    if (getFraction() > 0) {
      myProgress.setValue((int)(getFraction() * 100));
    }

    revalidate |= updateComponent(myText2, !myCompact && getText2() != null && getText2().length() > 0, BorderLayout.SOUTH);

    myText.setText(getText());
    myText2.setText(getText2());

    myCancelButton.setActive(isCancelable());

    if (revalidate) {
      UIUtil.removeQuaquaVisualMarginsIn(myComponent);
      revalidate();
    }
    myComponent.repaint();
  }

  protected void revalidate() {
    myComponent.revalidate();
  }


  private boolean updateComponent(JComponent component, boolean holdsValue, String layoutConstraint) {
    if (holdsValue && component.getParent() == null) {
      myComponent.add(component, layoutConstraint);
      return true;
    }
    else if (!holdsValue && component.getParent() != null) {
      myComponent.remove(component);
      return true;
    }

    return false;
  }

  public abstract static class InlineButton extends JButton {

    private boolean myActive = true;

    public InlineButton(Icon icon, String tooltip) {
      super(icon);
      setBorder(null);
      addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (!myActive) return;
          onActionPerformed(e);
        }
      });
      setToolTipText(tooltip);
    }

    public void setActive(final boolean active) {
      myActive = active;
    }


    public boolean isActive() {
      return myActive;
    }

    protected abstract void onActionPerformed(final ActionEvent e);

    protected void paintComponent(Graphics g) {
      if (!myActive) return;
      super.paintComponent(g);
    }
  }

  public void setText(String text) {
    super.setText(text);
    queueUpdate();
  }

  public void setText2(String text) {
    super.setText2(text);
    queueUpdate();
  }


  public void setFraction(double fraction) {
    super.setFraction(fraction);
    queueUpdate();
  }


  public void setIndeterminate(boolean indeterminate) {
    super.setIndeterminate(indeterminate);
    queueUpdate();
  }


  public void pushState() {
    super.pushState();
    queueUpdate();
  }


  public void popState() {
    super.popState();
    queueUpdate();
  }

  public void startNonCancelableSection() {
    super.startNonCancelableSection();
    queueUpdate();
  }

  public void finishNonCancelableSection() {
    super.finishNonCancelableSection();
    queueUpdate();
  }
  

}
