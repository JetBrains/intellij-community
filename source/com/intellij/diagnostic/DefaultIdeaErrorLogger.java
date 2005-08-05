/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.ErrorLogger;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;

/**
 * @author kir
 */
public class DefaultIdeaErrorLogger implements ErrorLogger {
  private static boolean ourOomOccured = false;

  public boolean canHandle(IdeaLoggingEvent event) {
    boolean notificationEnabled = !"disabled".equals(System.getProperty("idea.fatal.error.notification", "enabled"));

    return  notificationEnabled ||
            ApplicationManagerEx.getApplicationEx().isInternal() ||
            event.getThrowable() instanceof OutOfMemoryError;
  }

  /** @noinspection CallToPrintStackTrace*/
  public void handle(IdeaLoggingEvent event) {
    try {
      if (event.getThrowable() instanceof OutOfMemoryError) {
        processOOMError(event);
      }
      else if (!ourOomOccured) {
        MessagePool.getInstance().addIdeFatalMessage(event);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  /** @noinspection CallToPrintStackTrace*/
  private void processOOMError(IdeaLoggingEvent event) throws InterruptedException, InvocationTargetException {
    final String message = event.getThrowable().getMessage();
    final String option = message != null && message.indexOf("PermGen") >= 0 ? "-XX:MaxPermSize" : "-Xmx";
    ourOomOccured = true;
    event.getThrowable().printStackTrace();
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        String message = "There's not enough memory to perform the requested operation.\n" +
                         "Please shutdown " + ApplicationNamesInfo.getInstance().getProductName() +
                         " and increase " + option + " setting in " + getSettingsFilePath();

        if (JOptionPane.showOptionDialog(JOptionPane.getRootFrame(), message, "Out of Memory",
                                         JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null,
                                         new Object[]{"Shutdown", "Ignore"}, "Shutdown") == 0) {
          try {
            ApplicationManager.getApplication().exit();
          }
          catch (Throwable e) {
            System.exit(0);
          }
        }
      }
    });
  }

  private String getLogFilePath() {
    String path = PathManager.getSystemPath() + "/log/idea.log file".replace('/', File.separatorChar);
    try {
      return new File(path).getAbsolutePath();
    }
    catch (Exception e) {
      return path;
    }
  }

  private String getSettingsFilePath() {
    if (SystemInfo.isMac) {
      return PathManager.getHomePath() + "/Contents/Info.plist";
    }
    else if (SystemInfo.isWindows) {
      return PathManager.getBinPath() + "\\idea.exe.vmoptions";
    }
    return PathManager.getBinPath() + "/idea.vmoptions".replace('/', File.separatorChar);
  }
}
