/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.ErrorLogger;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.util.SystemInfo;

import javax.swing.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author kir
 */
public class DefaultIdeaErrorLogger implements ErrorLogger {

  private static boolean ourOomOccured = false;
  private final List<IdeaLoggingEvent> myEventsCache = Collections.synchronizedList(new ArrayList<IdeaLoggingEvent>());

  public boolean canHandle(IdeaLoggingEvent event) {
    return !DialogAppender.RELEASE_BUILD ||
          ApplicationManagerEx.getApplicationEx().isInternal() ||
          event.getThrowable() instanceof OutOfMemoryError;
  }

  public void handle(IdeaLoggingEvent event) {
    showDialog(event);
  }

  private void showDialog(IdeaLoggingEvent event) {
    try {
      if (event.getThrowable() instanceof OutOfMemoryError) {
        processOOMError(event);
      }
      else {
        if (ourOomOccured) {
          SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
              JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                            "OutOfMemoryException occured before this problem and " +
                                            "it can be the reason of current one. So we recommend " +
                                            "you to restart your IDEA.", "Error", JOptionPane.ERROR_MESSAGE);
            }
          });
          return;
        }
        myEventsCache.add(event);
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            while (myEventsCache.size() > 0) {
              IdeaLoggingEvent event = myEventsCache.get(0);
              ErrorOccuredDialog errorOccuredDialog = new ErrorOccuredDialog(event);
              errorOccuredDialog.show();

              if (errorOccuredDialog.isShouldClose()) {
                try {
                  ApplicationManager.getApplication().exit();
                }
                catch (Throwable e) {
                  System.exit(0);
                }
              }
              myEventsCache.remove(0);
            }
          }
        });
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void processOOMError(IdeaLoggingEvent event) throws InterruptedException, InvocationTargetException {
    final String logFilePath = getLogFilePath();

    ourOomOccured = true;
    event.getThrowable().printStackTrace();
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        String message = "There's not enough memory to perform the requested operation.";
        message += "\n" + "Please shutdown IDEA and increase -Xmx setting in " + getSettingsFilePath();
        message += "\n" + ReportMessages.getReportAddress();
        message += "\n" + "and attach the " + logFilePath;
        message += "\n" + "with a brief description of what you were doing.";

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
    return PathManager.getBinPath() + "/idea.lax".replace('/', File.separatorChar);
  }

}
