package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.license.LicenseManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * Created by IntelliJ IDEA.
 * User: pti
 * Date: Jun 24, 2005
 * Time: 10:26:21 PM
 * To change this template use File | Settings | File Templates.
 */

class UpdateInfoDialog extends DialogWrapper {
  private UpdateInfoPanel myUpdateInfoPanel;
  private UpdateChecker.NewVersion myNewVersion;

  protected UpdateInfoDialog(final boolean canBeParent, UpdateChecker.NewVersion newVersion) {
    super(canBeParent);
    myNewVersion = newVersion;
    setTitle("Update Info");
    init();
  }

  protected JComponent createCenterPanel() {
    myUpdateInfoPanel = new UpdateInfoPanel();
    return myUpdateInfoPanel.myPanel;
  }

  protected Action[] createActions() {
    final Action cancelAction = getCancelAction();
    cancelAction.putValue(Action.NAME, "&Close");
    final Action okAction = getOKAction();
    okAction.putValue(Action.NAME, "&More Info...");
    return new Action[] {cancelAction, okAction};
  }

  protected void doOKAction() {
    BrowserUtil.launchBrowser(getDownloadUrl());
  }

  private String getDownloadUrl() {
    return ApplicationInfoEx.getInstanceEx().getUpdateUrls().getDownloadUrl();
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public void setLinkEnabled(final boolean enableLink) {
    if (enableLink) {
      myUpdateInfoPanel.myUpdatesLink.setForeground(Color.BLUE); // TODO: specify correct color
      myUpdateInfoPanel.myUpdatesLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      myUpdateInfoPanel.myUpdatesLink.setToolTipText("Click to open Updates Settings dialog");
      myUpdateInfoPanel.myUpdatesLink.addMouseListener(new MouseListener() {
        public void mouseClicked(MouseEvent e) {
          UpdateSettingsConfigurable updatesSettings = UpdateSettingsConfigurable.getInstance();
          updatesSettings.setCheckNowEnabled(false);
          ShowSettingsUtil.getInstance().editConfigurable(myUpdateInfoPanel.myPanel, updatesSettings);
          updatesSettings.setCheckNowEnabled(true);
        }
        public void mouseEntered(MouseEvent e) {
        }
        public void mouseExited(MouseEvent e) {
        }
        public void mousePressed(MouseEvent e) {
        }
        public void mouseReleased(MouseEvent e) {
        }
      });
    }
  }

  private class UpdateInfoPanel {

    private JPanel myPanel;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JLabel myNewVersionNumber;
    private JLabel myNewBuildNumber;
    private JLabel myUpdatesLink;

    public UpdateInfoPanel() {

      final String build = ApplicationInfo.getInstance().getBuildNumber().trim();
      myBuildNumber.setText(build + ")");
      final String majorVersion = ApplicationInfo.getInstance().getMajorVersion();
      final String version;
      if (majorVersion != null && majorVersion.trim().length() > 0) {
        final String minorVersion = ApplicationInfo.getInstance().getMinorVersion();
        if (minorVersion != null && minorVersion.trim().length() > 0) {
          version = majorVersion + "." + minorVersion;
        }
        else {
          version = majorVersion + ".0";
        }
      }
      else {
        version = ApplicationInfo.getInstance().getVersionName();
      }

      myVersionNumber.setText(version);
      myNewBuildNumber.setText(Integer.toString(myNewVersion.getLatestBuild()) + ")");
      myNewVersionNumber.setText(myNewVersion.getLatestVersion());

      LabelTextReplacingUtil.replaceText(myPanel);
    }

    public JComponent getComponent() {
      return myPanel;
    }

    public void dispose() {
    }
  }

}
