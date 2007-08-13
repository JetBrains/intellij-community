package com.intellij.openapi.updateSettings.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
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
  private final String myUploadedPlugins;

  protected UpdateInfoDialog(final boolean canBeParent, UpdateChecker.NewVersion newVersion, final String uploadedPlugins) {
    super(canBeParent);
    myNewVersion = newVersion;
    myUploadedPlugins = uploadedPlugins;
    setTitle(IdeBundle.message("updates.info.dialog.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    myUpdateInfoPanel = new UpdateInfoPanel();
    return myUpdateInfoPanel.myPanel;
  }

  protected Action[] createActions() {
    final Action cancelAction = getCancelAction();
    cancelAction.putValue(Action.NAME, CommonBundle.getCloseButtonText());
    final Action okAction = getOKAction();
    okAction.putValue(Action.NAME, IdeBundle.message("updates.more.info.button"));
    return new Action[] {cancelAction, okAction};
  }

  protected void doOKAction() {
    BrowserUtil.launchBrowser(getDownloadUrl());
    if (myUploadedPlugins != null) {
      ApplicationManagerEx.getApplicationEx().exit(true);
    }
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
      myUpdateInfoPanel.myUpdatesLink.setToolTipText(IdeBundle.message("updates.open.settings.link"));
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
    private JLabel myUpdatedPlugins;

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
      myUpdatedPlugins.setText(myUploadedPlugins != null ? myUploadedPlugins : "");
      myUpdatedPlugins.setVisible(myUploadedPlugins != null);

      LabelTextReplacingUtil.replaceText(myPanel);
    }

    public JComponent getComponent() {
      return myPanel;
    }

    public void dispose() {
    }
  }

}
