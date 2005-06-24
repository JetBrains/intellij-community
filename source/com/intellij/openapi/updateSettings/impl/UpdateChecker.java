/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Oct 31, 2002
 * Time: 6:33:01 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.actions.CheckForUpdateAction;
import com.intellij.ide.license.LicenseManager;
import com.intellij.ide.reporter.ConnectionException;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.text.DateFormatUtil;
import org.jdom.Document;

import java.awt.*;
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
public final class UpdateChecker implements ApplicationComponent, ProjectManagerListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.updateSettings.impl.UpdateChecker");

  private static final URL UPDATE_URL;

  private static long CHECK_INTERVAL = 0;
  private static boolean alreadyChecked = false;
  private static boolean myVeryFirstProjectOpening = true;

  public static NewVersion NEW_VERION = null;

  public UpdateChecker (ProjectManager projectManager) {
    projectManager.addProjectManagerListener(this);
  }

    public String getComponentName() {
    return "UpdateChecker";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    ProjectManager.getInstance().removeProjectManagerListener(this);
  }

  public static void setAlreadyChecked(final boolean checked) {
    alreadyChecked = checked;
  }

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
    if ("__BUILD_NUMBER__".equals(ourBuild)) ourBuild = "123";// TODO: change to correct behavior
                                                              // Integer.toString(Integer.MAX_VALUE);

    if (LOG.isDebugEnabled()) {
      LOG.debug("build available:'" + availBuild + "' ourBuild='" + ourBuild + "' ");
    }

    try {
      final int iAvailBuild = Integer.parseInt(availBuild);
      final int iOurBuild = Integer.parseInt(ourBuild);
      if (iAvailBuild > iOurBuild) {
        NEW_VERION = new NewVersion(iAvailBuild, availVersion);
      }

    }
    catch (Throwable t) {
      LOG.debug(t);
      return;
    }

    UpdateSettingsConfigurable.getInstance().LAST_TIME_CHECKED = System.currentTimeMillis();
  }

  public static boolean checkNeeded() {
    if (!LicenseManager.getInstance().shouldCheckForUpdates() || alreadyChecked) {
        return false;
    }
    final UpdateSettingsConfigurable settings = UpdateSettingsConfigurable.getInstance();
    if (settings == null || UPDATE_URL == null) return false;

    final String checkPeriod = settings.CHECK_PERIOD;
    if (checkPeriod.equals(UpdateSettingsConfigurable.ON_START_UP)) {
      CHECK_INTERVAL = 0;
    }
    if (checkPeriod.equals(UpdateSettingsConfigurable.DAILY)) {
      CHECK_INTERVAL = DateFormatUtil.DAY;
    }
    if (settings.CHECK_PERIOD.equals(UpdateSettingsConfigurable.WEEKLY)) {
      CHECK_INTERVAL = DateFormatUtil.WEEK;
    }
    if (settings.CHECK_PERIOD.equals(UpdateSettingsConfigurable.MONTHLY)) {
      CHECK_INTERVAL = DateFormatUtil.MONTH;
    }

    final long timeDelta = System.currentTimeMillis() - settings.LAST_TIME_CHECKED;
    if (Math.abs(timeDelta) < CHECK_INTERVAL) return false;

    return settings.CHECK_NEEDED;
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

  public void projectOpened(Project project) {
    if (!myVeryFirstProjectOpening || ApplicationManagerEx.getApplicationEx().isInternal() ||
        !GeneralSettings.getInstance().isReopenLastProject()
      ) {
      return;
    }
    myVeryFirstProjectOpening = false;

    if (checkNeeded()) {
      CheckForUpdateAction.actionPerformed();
    }
  }

  public boolean canCloseProject(Project project) {
    return true;
  }

  public void projectClosed(Project project) {}

  public void projectClosing(Project project) {}

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
