/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Oct 31, 2002
 * Time: 6:33:01 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.ide.updates;

import com.intellij.ide.license.LicenseManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Document;

import javax.swing.*;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * XML sample:
 * <idea>
 * <build>456</build>
 * <title>New Intellij IDEA Version</title>
 * <message>
 * New version of IntelliJ IDEA is available.
 * Please visit http://www.intellij.com/ for more info.
 * </message>
 * </idea>
 */
final class UpdateChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.updates.UpdateChecker");

  private static final int MIN = 1000 * 60;
  private static final long CHECK_INTERVAL = MIN * 60 * 24 * 2;
  private static final URL UPDATE_URL;

  static {
    URL url = null;
    try {
      url = new URL("http://www.jetbrains.com/updates/update.xml");
    }
    catch (MalformedURLException e) {
      LOG.error(e);
    }

    UPDATE_URL = url;
  }

  public static void checkForUpdates() {
    if (!LicenseManager.getInstance().shouldCheckForUpdates()) {
      return;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: checkForUpdates()");
    }
    final UpdateSettings settings = UpdateSettings.getInstance();
    if (settings == null) return;
    if (UPDATE_URL == null) return;

    final long timeDelta = System.currentTimeMillis() - settings.LAST_TIME_CHECKED;
    if (Math.abs(timeDelta) < CHECK_INTERVAL) return;

    if (!settings.ASK_USER && !settings.CHECK_UPDATES) return;

    if (settings.ASK_USER) {
      final ConfirmUpdateDialog confirmUpdateDialog = new ConfirmUpdateDialog();
      confirmUpdateDialog.show();
      settings.CHECK_UPDATES = confirmUpdateDialog.getExitCode() == ConfirmUpdateDialog.OK_EXIT_CODE;
      settings.ASK_USER = !confirmUpdateDialog.getCheckBox().isSelected();
      if (confirmUpdateDialog.getExitCode() != ConfirmUpdateDialog.OK_EXIT_CODE) return;
    }

    new Thread(new Runnable() {
      public void run() {
        final Document document;
        try {
          document = loadVersionInfo();
        }
        catch (Throwable t) {
          LOG.debug(t);
          return;
        }

        final String message = document.getRootElement().getChild("message").getTextTrim();
        final String title = document.getRootElement().getChild("title").getTextTrim();
        final String availBuild = document.getRootElement().getChild("build").getTextTrim();
        final String ourBuild = ApplicationInfo.getInstance().getBuildNumber().trim();

        if (LOG.isDebugEnabled()) {
          LOG.debug("build available:'" + availBuild + "' ourBuild='" + ourBuild + "' ");
        }

        try {
          final int iAvailBuild = Integer.parseInt(availBuild);
          final int iOurBuild = Integer.parseInt(ourBuild);
          if (iAvailBuild > iOurBuild) {
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                final UpdateAvailDialog dialog = new UpdateAvailDialog(title, message.trim());
                dialog.show();
              }
            });
          }

        }
        catch (Throwable t) {
          LOG.debug(t);
          return;
        }

        settings.LAST_TIME_CHECKED = System.currentTimeMillis();
      }
    }, "UpdateCheckingThread").start();

  }

  private static Document loadVersionInfo() throws Exception {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: loadVersionInfo(UPDATE_URL='" + UPDATE_URL + "' )");
    }
    final InputStream inputStream = UPDATE_URL.openStream();
    final Document document;
    try {
      document = JDOMUtil.loadDocument(inputStream);
    }
    finally {
      inputStream.close();
    }

    return document;
  }
}
