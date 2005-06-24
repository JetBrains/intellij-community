package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.*;
import com.intellij.ide.actions.CheckForUpdateAction;
import org.jdom.Element;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import java.awt.*;
import java.text.DateFormat;

/**
 * Created by IntelliJ IDEA.
 * User: pti
 * Date: Jun 10, 2005
 * Time: 12:39:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class UpdateSettingsConfigurable extends BaseConfigurable implements ApplicationComponent, JDOMExternalizable {
  private UpdatesSettingsPanel myUpdatesSettingsPanel;

  public static final String ON_START_UP = "On every start";
  public static final String DAILY = "Daily";
  public static String WEEKLY = "Weekly";
  public static String MONTHLY = "Monthly";

  public boolean CHECK_NEEDED = true;
  public String CHECK_PERIOD = WEEKLY;
  public long LAST_TIME_CHECKED = 0;


  public UpdateSettingsConfigurable() {
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public String getDisplayName() {
    return "Updates";
  }

  public JComponent createComponent() {
    myUpdatesSettingsPanel = new UpdatesSettingsPanel();
    return myUpdatesSettingsPanel.myPanel;
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/confidurableUpdates.png");
  }

  public void apply() throws ConfigurationException {
    CHECK_PERIOD = (String)myUpdatesSettingsPanel.myPeriodCombo.getSelectedItem();
    CHECK_NEEDED = myUpdatesSettingsPanel.myCbCheckForUpdates.isSelected();
  }

  public void reset() {
    myUpdatesSettingsPanel.myCbCheckForUpdates.setSelected(CHECK_NEEDED);
    myUpdatesSettingsPanel.myPeriodCombo.setSelectedItem(CHECK_PERIOD);
    myUpdatesSettingsPanel.myPeriodCombo.setEnabled(myUpdatesSettingsPanel.myCbCheckForUpdates.isSelected());
    myUpdatesSettingsPanel.updateLastCheckedLabel();
  }

  public boolean isModified() {
      return CHECK_NEEDED != myUpdatesSettingsPanel.myCbCheckForUpdates.isSelected() ||
           !CHECK_PERIOD.equals(myUpdatesSettingsPanel.myPeriodCombo.getSelectedItem());
  }

  public void disposeUIResources() {
    myUpdatesSettingsPanel = null;
  }

  public String getHelpTopic() {
    return "preferences.versionUpdates"; //TODO[pti]: request Help Topic
  }

  public String getComponentName() {
    return "UpdatesConfigurable";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public static UpdateSettingsConfigurable getInstance() {
    return ApplicationManager.getApplication().getComponent(UpdateSettingsConfigurable.class);
  }

  public void showUpdateInfoDialog() {
    // TODO: position the dialog
    //Component focusedComponent = e.getInputEvent().getComponent();

    JDialog dialog = new JDialog();
    UpdateInfoPanel updateInfoPanel = new UpdateInfoPanel();
    dialog.add(updateInfoPanel.myPanel);
    dialog.setTitle("Update Info");
    dialog.pack();
    dialog.show();
  }

  public void showNoUpdatesDialog() {
    // TODO: position the dialog
    //Component focusedComponent = e.getInputEvent().getComponent();

    JDialog dialog = new JDialog();
    NoUpdatesPanel noUpdatesPanel = new NoUpdatesPanel();
    dialog.add(noUpdatesPanel.myPanel);
    dialog.setTitle("Update Info");
    dialog.pack();
    dialog.show();
  }

  private class UpdatesSettingsPanel {

    private JPanel myPanel;
    private JButton myBtnCheckNow;
    private JComboBox myPeriodCombo;
    private JCheckBox myCbCheckForUpdates;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JLabel myLastCheckedDate;

    public UpdatesSettingsPanel() {
      myPeriodCombo.addItem(ON_START_UP);
      myPeriodCombo.addItem(DAILY);
      myPeriodCombo.addItem(WEEKLY);
      myPeriodCombo.addItem(MONTHLY);

      String currentMajorVersion = ApplicationInfo.getInstance().getMajorVersion();
      String currentMinorVersion = ApplicationInfo.getInstance().getMinorVersion();

      String currentVersion;
      if (currentMajorVersion == null ) {
        currentVersion = "N/A";
      }
      else if (currentMinorVersion == null) {
        currentVersion = currentMajorVersion;
      }
      else {
        currentVersion = currentMajorVersion + "." + currentMinorVersion;
      }
      myVersionNumber.setText(currentVersion);

      String currentBuild = (ApplicationInfo.getInstance().getBuildNumber() == null) ? "N/A" : ApplicationInfo.getInstance().getBuildNumber();
      myBuildNumber.setText(currentBuild);

      myCbCheckForUpdates.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myPeriodCombo.setEnabled(myCbCheckForUpdates.isSelected());
        }
      });

      myBtnCheckNow.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          CheckForUpdateAction.actionPerformed();
          updateLastCheckedLabel();
        }
      });
    }

    private void updateLastCheckedLabel() {
      final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
      myLastCheckedDate.setText(LAST_TIME_CHECKED == 0 ? "Never" : dateFormat.format(LAST_TIME_CHECKED));
    }
  }

  // TODO: extract class
  private static class UpdateInfoPanel {

    private JPanel myPanel;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JButton myBtnMoreInfo;
    private JButton myBtnUpdateLater;
    private JLabel myNewVersionNumber;
    private JLabel myNewBuildNumber;
    private JLabel myUpdatesLink;

    public UpdateInfoPanel() {
      myBtnMoreInfo.setSelected(true);
      myBtnUpdateLater.setSelected(false);

      final String build = ApplicationInfo.getInstance().getBuildNumber().trim();
      myBuildNumber.setText(build + ")");
      String version = ApplicationInfo.getInstance().getMajorVersion() + "." + ApplicationInfo.getInstance().getMajorVersion();
      if (version.equalsIgnoreCase("null.null")) {
        version = ApplicationInfo.getInstance().getVersionName();
      }
      myVersionNumber.setText(version);
      myNewBuildNumber.setText(Integer.toString(UpdateChecker.NEW_VERION.getLatestBuild()) + ")");
      myNewVersionNumber.setText(UpdateChecker.NEW_VERION.getLatestVersion());

      // TODO: add action listener for the Close button

      myUpdatesLink.setForeground(Color.BLUE); // TODO: specify correct color
      myUpdatesLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      myUpdatesLink.addMouseListener(new MouseListener() {
        public void mouseClicked(MouseEvent e) {
          ShowSettingsUtil.getInstance().editConfigurable(myPanel, UpdateSettingsConfigurable.getInstance());
        }

        public void mouseEntered(MouseEvent e) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        public void mouseExited(MouseEvent e) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        public void mousePressed(MouseEvent e) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        public void mouseReleased(MouseEvent e) {
          //To change body of implemented methods use File | Settings | File Templates.
        }
      });
    }

    public JComponent getComponent() {
      return myPanel;
    }

    public void dispose() {
    }
  }

  // TODO: extract class
  private static class NoUpdatesPanel {
    private JLabel myUpdatesLink;
    private JButton myCloseButton;
    private JPanel myPanel;

    public NoUpdatesPanel() {
      myCloseButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          ((Window)myPanel.getParent()).dispose(); // TODO: fix wrong class cast
        }
      });

      myUpdatesLink.setForeground(Color.BLUE); // TODO: specify correct color
      myUpdatesLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      myUpdatesLink.addMouseListener(new MouseListener() {
        public void mouseClicked(MouseEvent e) {
          ShowSettingsUtil.getInstance().editConfigurable(myPanel, UpdateSettingsConfigurable.getInstance());
        }

        public void mouseEntered(MouseEvent e) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        public void mouseExited(MouseEvent e) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        public void mousePressed(MouseEvent e) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        public void mouseReleased(MouseEvent e) {
          //To change body of implemented methods use File | Settings | File Templates.
        }
      });
    }
  }
}
