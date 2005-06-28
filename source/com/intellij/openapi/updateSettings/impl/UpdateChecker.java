/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Oct 31, 2002
 * Time: 6:33:01 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.license.LicenseManager;
import com.intellij.ide.reporter.ConnectionException;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jdom.Document;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * XML sample:
 * <idea>
 * <build>456</build>
 * <version>4.5.2</version>
 * <title>New Intellij IDEA Version</title>
 * <message>
 * New version of IntelliJ IDEA is available.
 * Please visit http://www.intellij.com/ for more info.
 * </message>
 * </idea>
 */
public final class UpdateChecker implements ApplicationComponent {

  public static NewVersion newVersion = null;

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.updateSettings.impl.UpdateChecker");
  private static final URL UPDATE_URL;

  private static long checkInterval = 0;
  private static boolean myVeryFirstOpening = true;

  static {
    URL url = null;
    String downloadUrl;
    if (LicenseManager.getInstance().isEap()) {
      downloadUrl = "http://www.intellij.net/eap/products/idea/redirect.jsp?filename=update.xml";
    }
    else {
      downloadUrl = "http://www.jetbrains.com/updates/update.xml";
    }
    try {
      url = new URL(downloadUrl);
    }
    catch (MalformedURLException e) {
      LOG.error(e);
    }

    UPDATE_URL = url;
  }

  public String getComponentName() {
    return "UpdateChecker";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static boolean isMyVeryFirstOpening() {
    return myVeryFirstOpening;
  }

  public static void setMyVeryFirstOpening(final boolean myVeryFirstProjectOpening) {
    UpdateChecker.myVeryFirstOpening = myVeryFirstProjectOpening;
  }

  public static boolean checkNeeded() {

    final UpdateSettingsConfigurable settings = UpdateSettingsConfigurable.getInstance();
    if (settings == null || UPDATE_URL == null) return false;

    final String checkPeriod = settings.CHECK_PERIOD;
    if (checkPeriod.equals(UpdateSettingsConfigurable.ON_START_UP)) {
      checkInterval = 0;
    }
    if (checkPeriod.equals(UpdateSettingsConfigurable.DAILY)) {
      checkInterval = DateFormatUtil.DAY;
    }
    if (settings.CHECK_PERIOD.equals(UpdateSettingsConfigurable.WEEKLY)) {
      checkInterval = DateFormatUtil.WEEK;
    }
    if (settings.CHECK_PERIOD.equals(UpdateSettingsConfigurable.MONTHLY)) {
      checkInterval = DateFormatUtil.MONTH;
    }

    final long timeDelta = System.currentTimeMillis() - settings.LAST_TIME_CHECKED;
    if (Math.abs(timeDelta) < checkInterval) return false;

    return settings.CHECK_NEEDED;
  }

  public static void checkForUpdates() throws ConnectionException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: checkForUpdates()");
    }

    final Document document;
    try {
      document = loadVersionInfo();
    }
    catch (Throwable t) {
      LOG.debug(t);
      throw new ConnectionException(t);
    }

    final String availBuild = document.getRootElement().getChild("build").getTextTrim();
    final String availVersion = document.getRootElement().getChild("version").getTextTrim();
    String ourBuild = ApplicationInfo.getInstance().getBuildNumber().trim();
    if ("__BUILD_NUMBER__".equals(ourBuild)) ourBuild = Integer.toString(Integer.MAX_VALUE);

    if (LOG.isDebugEnabled()) {
      LOG.debug("build available:'" + availBuild + "' ourBuild='" + ourBuild + "' ");
    }

    try {
      final int iAvailBuild = Integer.parseInt(availBuild);
      final int iOurBuild = Integer.parseInt(ourBuild);
      if (iAvailBuild > iOurBuild) {
        newVersion = new NewVersion(iAvailBuild, availVersion);
      }

    }
    catch (Throwable t) {
      LOG.debug(t);
      return;
    }

    UpdateSettingsConfigurable.getInstance().LAST_TIME_CHECKED = System.currentTimeMillis();
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

  public static void showNoUpdatesDialog(boolean enableLink) {
    NoUpdatesDialog dialog = new NoUpdatesDialog(true);
    dialog.setLinkEnabled(enableLink);
    dialog.setResizable(false);
    dialog.show();
  }

  public static void showUpdateInfoDialog(boolean enableLink) {
    UpdateInfoDialog dialog = new UpdateInfoDialog(true);
    dialog.setLinkEnabled(enableLink);
    dialog.setResizable(false);
    dialog.show();
  }

  public static class NewVersion {

    private static int latestBuild;
    private String latestVersion;

    public int getLatestBuild() {
      return latestBuild;
    }

    public String getLatestVersion() {
      return latestVersion;
    }

    public NewVersion(int build, String version) {
      latestBuild = build;
      latestVersion = version;
    }
  }
}
