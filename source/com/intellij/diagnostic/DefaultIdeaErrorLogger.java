/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.ErrorLogger;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;

/**
 * @author kir
 */
public class DefaultIdeaErrorLogger implements ErrorLogger {
  private static boolean ourOomOccured = false;
  @NonNls private static final String FATAL_ERROR_NOTIFICATION_PROPERTY = "idea.fatal.error.notification";
  @NonNls private static final String DISABLED_VALUE = "disabled";
  @NonNls private static final String ENABLED_VALUE = "enabled";
  @NonNls private static final String PARAM_PERMGEN = "PermGen";
  @NonNls private static final String PARAM_MAXPERMSIZE = "-XX:MaxPermSize";
  @NonNls private static final String PARAM_XMX = "-Xmx";
  @NonNls private static final String IDEA_LOG_PATH = "/log/idea.log";
  @NonNls private static final String INFO_PLIST = "/Contents/Info.plist";
  @NonNls private static final String IDEA_EXE_VMOPTIONS = "\\idea.exe.vmoptions";
  @NonNls private static final String IDEA_VMOPTIONS = "/idea.vmoptions";

  public boolean canHandle(IdeaLoggingEvent event) {
    boolean notificationEnabled = !DISABLED_VALUE.equals(System.getProperty(FATAL_ERROR_NOTIFICATION_PROPERTY, ENABLED_VALUE));

    return  notificationEnabled ||
            !(IdeErrorsDialog.getSubmitter(event.getThrowable()) instanceof ITNReporter) ||
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
    final String option = message != null && message.indexOf(PARAM_PERMGEN) >= 0 ? PARAM_MAXPERMSIZE : PARAM_XMX;
    ourOomOccured = true;
    event.getThrowable().printStackTrace();
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        String message = DiagnosticBundle.message("diagnostic.out.of.memory.error",
                                                  ApplicationNamesInfo.getInstance().getProductName(),
                                                  option, getSettingsFilePath());

        if (JOptionPane.showOptionDialog(JOptionPane.getRootFrame(), message, DiagnosticBundle.message("diagnostic.out.of.memory.title"),
                                         JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null,
                                         new Object[]{
                                           DiagnosticBundle.message("diagnostic.out.of.memory.shutdown"),
                                           DiagnosticBundle.message("diagnostic.out.of.memory.ignore")
                                         }, DiagnosticBundle.message("diagnostic.out.of.memory.shutdown")) == 0) {
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
    String path = PathManager.getSystemPath() + IDEA_LOG_PATH.replace('/', File.separatorChar);
    try {
      return new File(path).getAbsolutePath();
    }
    catch (Exception e) {
      return path;
    }
  }

  private String getSettingsFilePath() {
    if (SystemInfo.isMac) {
      return PathManager.getHomePath() + INFO_PLIST;
    }
    else if (SystemInfo.isWindows) {
      return PathManager.getBinPath() + IDEA_EXE_VMOPTIONS;
    }
    return PathManager.getBinPath() + IDEA_VMOPTIONS.replace('/', File.separatorChar);
  }
}
