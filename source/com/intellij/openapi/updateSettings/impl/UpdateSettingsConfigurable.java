package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.ui.ListUtil;
import com.intellij.util.ui.MappingListCellRenderer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DateFormat;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: pti
 * Date: Jun 10, 2005
 * Time: 12:39:08 PM
 * To change this template use File | Settings | File Templates.
 */

public class UpdateSettingsConfigurable extends BaseConfigurable implements SearchableConfigurable, ApplicationComponent, JDOMExternalizable {
  private UpdatesSettingsPanel myUpdatesSettingsPanel;
  private boolean myCheckNowEnabled = true;
  @NonNls public static final String ON_START_UP = "On every start";
  @NonNls public static final String DAILY = "Daily";
  @NonNls public static final String WEEKLY = "Weekly";
  @NonNls public static final String MONTHLY = "Monthly";
  private static final Map<Object, String> PERIOD_VALUE_MAP = new HashMap<Object, String>();

  public JDOMExternalizableStringList myPluginHosts = new JDOMExternalizableStringList();

  static {
    PERIOD_VALUE_MAP.put(ON_START_UP, IdeBundle.message("updates.check.period.on.startup"));
    PERIOD_VALUE_MAP.put(DAILY, IdeBundle.message("updates.check.period.daily"));
    PERIOD_VALUE_MAP.put(WEEKLY, IdeBundle.message("updates.check.period.weekly"));
    PERIOD_VALUE_MAP.put(MONTHLY, IdeBundle.message("updates.check.period.monthly"));
  }

  public boolean CHECK_NEEDED = true;
  public String CHECK_PERIOD = WEEKLY;
  public long LAST_TIME_CHECKED = 0;

  public JComponent createComponent() {
    myUpdatesSettingsPanel = new UpdatesSettingsPanel();
    return myUpdatesSettingsPanel.myPanel;
  }

  @NotNull
  public String getComponentName() {
    return "UpdatesConfigurable";
  }

  public String getDisplayName() {
    return IdeBundle.message("updates.settings.title");
  }

  public String getHelpTopic() {
    return "preferences.updates";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableUpdates.png");
  }

  public static UpdateSettingsConfigurable getInstance() {
    return ApplicationManager.getApplication().getComponent(UpdateSettingsConfigurable.class);
  }

  public void setCheckNowEnabled(boolean enabled) {
    myCheckNowEnabled = enabled;
  }

  public void apply() throws ConfigurationException {
    CHECK_PERIOD = (String)myUpdatesSettingsPanel.myPeriodCombo.getSelectedItem();
    CHECK_NEEDED = myUpdatesSettingsPanel.myCbCheckForUpdates.isSelected();

    myPluginHosts.clear();
    myPluginHosts.addAll(myUpdatesSettingsPanel.getPluginsHosts());
  }

  public void reset() {
    myUpdatesSettingsPanel.myCbCheckForUpdates.setSelected(CHECK_NEEDED);
    myUpdatesSettingsPanel.myPeriodCombo.setSelectedItem(CHECK_PERIOD);
    myUpdatesSettingsPanel.myPeriodCombo.setEnabled(myUpdatesSettingsPanel.myCbCheckForUpdates.isSelected());
    myUpdatesSettingsPanel.updateLastCheckedLabel();
    myUpdatesSettingsPanel.setPluginHosts(myPluginHosts);
  }

  public boolean isModified() {
    if (!myPluginHosts.equals(myUpdatesSettingsPanel.getPluginsHosts())) return true;
    return CHECK_NEEDED != myUpdatesSettingsPanel.myCbCheckForUpdates.isSelected() ||
           !Comparing.equal(CHECK_PERIOD, myUpdatesSettingsPanel.myPeriodCombo.getSelectedItem());
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void disposeUIResources() {
    myUpdatesSettingsPanel = null;
  }

  public List<String> getPluginHosts() {
    if (myUpdatesSettingsPanel != null) {
      return myUpdatesSettingsPanel.getPluginsHosts();
    }
    return myPluginHosts;
  }

  private class UpdatesSettingsPanel {

    private JPanel myPanel;
    private JButton myBtnCheckNow;
    private JComboBox myPeriodCombo;
    private JCheckBox myCbCheckForUpdates;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JLabel myLastCheckedDate;

    private JButton myAddButton;
    private JButton myDeleteButton;
    private JList myUrlsList;
    private JButton myEditButton;

    public UpdatesSettingsPanel() {

      myPeriodCombo.addItem(ON_START_UP);
      myPeriodCombo.addItem(DAILY);
      myPeriodCombo.addItem(WEEKLY);
      myPeriodCombo.addItem(MONTHLY);

      myPeriodCombo.setRenderer(new MappingListCellRenderer(PERIOD_VALUE_MAP));

      final String majorVersion = ApplicationInfo.getInstance().getMajorVersion();
      String versionNumber = "";
      if (majorVersion != null && majorVersion.trim().length() > 0) {
        final String minorVersion = ApplicationInfo.getInstance().getMinorVersion();
        if (minorVersion != null && minorVersion.trim().length() > 0) {
          versionNumber = majorVersion + "." + minorVersion;
        }
        else {
          versionNumber = majorVersion + ".0";
        }
      }
      myVersionNumber.setText(ApplicationInfo.getInstance().getVersionName() + " " + versionNumber);
      String currentBuild = (ApplicationInfo.getInstance().getBuildNumber() == null)
                            ? IdeBundle.message("updates.current.build.unknown")
                            : ApplicationInfo.getInstance().getBuildNumber();
      myBuildNumber.setText(currentBuild);

      myCbCheckForUpdates.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myPeriodCombo.setEnabled(myCbCheckForUpdates.isSelected());
        }
      });

      myBtnCheckNow.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          CheckForUpdateAction.actionPerformed(false);
          updateLastCheckedLabel();
        }
      });
      myBtnCheckNow.setEnabled(myCheckNowEnabled);

      LabelTextReplacingUtil.replaceText(myPanel);

      myUrlsList.setModel(new DefaultListModel());
      myUrlsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      myUrlsList.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(final ListSelectionEvent e) {
          myDeleteButton.setEnabled(ListUtil.canRemoveSelectedItems(myUrlsList));
          myEditButton.setEnabled(ListUtil.canRemoveSelectedItems(myUrlsList));
        }
      });

      myAddButton.addActionListener(new ActionListener(){
        public void actionPerformed(final ActionEvent e) {
          final String input = Messages.showInputDialog(myPanel, IdeBundle.message("update.plugin.host.url.message"), IdeBundle.message("update.add.new.plugin.host.title"), Messages.getQuestionIcon(), "", new InputValidator() {
            public boolean checkInput(final String inputString) {
              return inputString.length() > 0;
            }

            public boolean canClose(final String inputString) {
              return checkInput(inputString);
            }
          });
          if (input != null) {
            ((DefaultListModel)myUrlsList.getModel()).addElement(input);
          }
        }
      });

      myEditButton.addActionListener(new ActionListener(){
        public void actionPerformed(final ActionEvent e) {
          final String input = Messages.showInputDialog(myPanel, IdeBundle.message("update.plugin.host.url.message"), IdeBundle.message("update.edit.plugin.host.title"), Messages.getQuestionIcon(), (String)myUrlsList.getSelectedValue(), new InputValidator() {
            public boolean checkInput(final String inputString) {
              return inputString.length() > 0;
            }

            public boolean canClose(final String inputString) {
              return checkInput(inputString);
            }
          });
          if (input != null) {
            ((DefaultListModel)myUrlsList.getModel()).set(myUrlsList.getSelectedIndex(), input);
          }
        }
      });

      myDeleteButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          ListUtil.removeSelectedItems(myUrlsList);
        }
      });
      myEditButton.setEnabled(false);
      myDeleteButton.setEnabled(false);
    }

    private void updateLastCheckedLabel() {
      final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.FULL);
      myLastCheckedDate
        .setText(LAST_TIME_CHECKED == 0 ? IdeBundle.message("updates.last.check.never") : dateFormat.format(new Date(LAST_TIME_CHECKED)));
    }

    public List<String> getPluginsHosts() {
      final List<String> result = new ArrayList<String>();
      for (int i = 0;i < myUrlsList.getModel().getSize(); i++) {
        result.add((String)myUrlsList.getModel().getElementAt(i));
      }
      return result;
    }

    public void setPluginHosts(final List<String> pluginHosts) {
      final DefaultListModel model = (DefaultListModel)myUrlsList.getModel();
      model.clear();
      for (String host : pluginHosts) {
        model.addElement(host);
      }
    }
  }

  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
