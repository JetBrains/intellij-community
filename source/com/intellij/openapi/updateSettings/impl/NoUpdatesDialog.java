package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.CommonBundle;

import javax.swing.*;
import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: pti
 * Date: Jun 24, 2005
 * Time: 10:44:40 PM
 * To change this template use File | Settings | File Templates.
 */

class NoUpdatesDialog extends DialogWrapper {
  private NoUpdatesPanel myNoUpdatesPanel;

  protected NoUpdatesDialog(final boolean canBeParent) {
    super(canBeParent);
    setTitle(IdeBundle.message("updates.info.dialog.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    myNoUpdatesPanel = new NoUpdatesPanel();
    return myNoUpdatesPanel.myPanel;
  }

  protected Action[] createActions() {
    final Action cancelAction = getCancelAction();
    cancelAction.putValue(Action.NAME, CommonBundle.getCloseButtonText());
    return new Action[] {cancelAction};
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public void setLinkEnabled(final boolean enableLink) {
    if (enableLink) {
      myNoUpdatesPanel.myUpdatesLink.setForeground(Color.BLUE); // TODO: specify correct color
      myNoUpdatesPanel.myUpdatesLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      myNoUpdatesPanel.myUpdatesLink.setToolTipText(IdeBundle.message("updates.open.settings.link"));
      myNoUpdatesPanel.myUpdatesLink.addMouseListener(new MouseListener() {
        public void mouseClicked(MouseEvent e) {
          UpdateSettingsConfigurable updatesSettings = UpdateSettingsConfigurable.getInstance();
          updatesSettings.setCheckNowEnabled(false);
          ShowSettingsUtil.getInstance().editConfigurable(myNoUpdatesPanel.myPanel, updatesSettings);
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

  private static class NoUpdatesPanel {
    private JLabel myUpdatesLink;
    private JPanel myPanel;

    public NoUpdatesPanel() {
      LabelTextReplacingUtil.replaceText(myPanel);
    }
  }
}
