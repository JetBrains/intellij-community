package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.ide.BrowserUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;

/**
 * Created by IntelliJ IDEA.
 * User: pti
 * Date: Jun 24, 2005
 * Time: 10:26:21 PM
 * To change this template use File | Settings | File Templates.
 */

class UpdateInfoDialog extends DialogWrapper {

  private static final String DOWNLOAD_URL = "http://www.jetbrains.com/idea/download/";
  private static UpdateInfoPanel myUpdateInfoPanel;

  protected UpdateInfoDialog(final boolean canBeParent) {
    super(canBeParent);
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
    BrowserUtil.launchBrowser(DOWNLOAD_URL);
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public void setLinkEnabled(final boolean enableLink) {
    if (enableLink) {
      myUpdateInfoPanel.myUpdatesLink.setForeground(Color.BLUE); // TODO: specify correct color
      myUpdateInfoPanel.myUpdatesLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

  private static class UpdateInfoPanel {

    private JPanel myPanel;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JLabel myNewVersionNumber;
    private JLabel myNewBuildNumber;
    private JLabel myUpdatesLink;

    public UpdateInfoPanel() {

      final String build = ApplicationInfo.getInstance().getBuildNumber().trim();
      myBuildNumber.setText(build + ")");
      String version = ApplicationInfo.getInstance().getMajorVersion() + "." + ApplicationInfo.getInstance().getMajorVersion();
      if (version.equalsIgnoreCase("null.null")) {
        version = ApplicationInfo.getInstance().getVersionName();
      }
      myVersionNumber.setText(version);
      myNewBuildNumber.setText(Integer.toString(UpdateChecker.NEW_VERION.getLatestBuild()) + ")");
      myNewVersionNumber.setText(UpdateChecker.NEW_VERION.getLatestVersion());
    }

    public JComponent getComponent() {
      return myPanel;
    }

    public void dispose() {
    }
  }

}
