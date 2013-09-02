package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class TimerLabel {
  private final JLabel myTimerLabel = new JLabel("XXX");
  private Timer myTimer;
  private long myInitialTime;

  public TimerLabel() {
    myTimerLabel.setVisible(false);
  }

  public JLabel getTimerLabel() {
    return myTimerLabel;
  }

  public void startCounter(final int cancelInterval, final Runnable runnable) {

    myTimerLabel.setVisible(true);
    myInitialTime = System.currentTimeMillis();

    myTimer = UIUtil.createNamedTimer("TimerLabel", 1 * 1000, new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateTimerLabel(cancelInterval);
        if (timerIsExpired(cancelInterval)) {
          if (myTimer != null) {
            runnable.run();
            if (myTimer != null) {
              myTimer.stop();
              myTimer = null;
            }
          }
        }
      }
    });
    updateTimerLabel(cancelInterval);
    myTimer.start();
  }

  private boolean timerIsExpired(final int cancelInterval) {
    return secondsFromStart() >= cancelInterval;
  }

  private void updateTimerLabel(int cancelInterval) {
    myTimerLabel.setText("Will be closed in " + Math.max(0, cancelInterval - secondsFromStart()) + " seconds");
  }

  private long secondsFromStart() {
    return (System.currentTimeMillis() - myInitialTime) / 1000;
  }

  public void stopCounter() {
    myTimerLabel.setVisible(false);
    if (myTimer != null) {
      myTimer.stop();
      myTimer = null;
    }
  }
}
