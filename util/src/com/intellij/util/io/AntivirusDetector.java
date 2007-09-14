/*
 * @author max
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

public class AntivirusDetector {
  private final static int THRESHOULD = 500;
  private boolean myEnabled = false;
  private Runnable myCallback;
  private static final AntivirusDetector ourInstance = new AntivirusDetector();

  public static AntivirusDetector getInstance() {
    return ourInstance;
  }

  private AntivirusDetector() {}

  public void enable(@NotNull Runnable callback) {
    myCallback = callback;
    myEnabled = true;
  }

  public void disable() {
    myEnabled = false;
  }

  public void execute(Runnable r) {
    if (!myEnabled) r.run();

    long now = System.currentTimeMillis();
    r.run();
    long delta = System.currentTimeMillis() - now;

    if (delta > THRESHOULD) {
      disable();
      myCallback.run();
    }
  }

}