package com.intellij.execution.impl;

import com.intellij.execution.ExecutionRegistry;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.openapi.options.*;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.Convertor;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author dyoma
 */
class ConfigurationSettingsEditor extends CompositeSettingsEditor<RunnerAndConfigurationSettings> {
  private ArrayList<SettingsEditor<RunnerAndConfigurationSettings>> myRunnerEditors = new ArrayList<SettingsEditor<RunnerAndConfigurationSettings>>();
  private RunnersEditorComponent myRunnersComponent;
  private RunConfiguration myConfiguration;
  private final SettingsEditor<RunConfiguration> myConfigurationEditor;
  private SettingsEditorGroup<RunnerAndConfigurationSettings> myCompound;
  private RunnerAndConfigurationSettings mySettings;

  public CompositeSettingsBuilder<RunnerAndConfigurationSettings> getBuilder() {
    init();
    return new GroupSettingsBuilder<RunnerAndConfigurationSettings>(myCompound);
  }

  private void init() {
    if (myCompound == null) {
      myCompound = new SettingsEditorGroup<RunnerAndConfigurationSettings>();
      if (myConfigurationEditor instanceof SettingsEditorGroup) {
        SettingsEditorGroup<RunConfiguration> group = (SettingsEditorGroup<RunConfiguration>)myConfigurationEditor;
        List<Pair<String, SettingsEditor<RunConfiguration>>> editors = group.getEditors();
        for (int i = 0; i < editors.size(); i++) {
          Pair<String, SettingsEditor<RunConfiguration>> pair = editors.get(i);
          myCompound.addEditor(pair.getFirst(), new ConfigToSettingsWrapper(pair.getSecond()));
        }
      }
      else {
        myCompound.addEditor("Configuration", new ConfigToSettingsWrapper(myConfigurationEditor));
      }


      myRunnersComponent = new RunnersEditorComponent();
      JavaProgramRunner[] runners = ExecutionRegistry.getInstance().getRegisteredRunners();
      for (int i = 0; i < runners.length; i++) {
        JavaProgramRunner runner = runners[i];
        JComponent perRunnerSettings = createCompositePerRunnerSettings(runner);
        if (perRunnerSettings != null) {
          myRunnersComponent.addRunnerComponent(runner, perRunnerSettings);
        }
      }

      if (myRunnerEditors.size() > 0) {
        myCompound.addEditor("Startup/Connection", new CompositeSettingsEditor<RunnerAndConfigurationSettings>(getFactory()) {
          public CompositeSettingsBuilder<RunnerAndConfigurationSettings> getBuilder() {
            return new CompositeSettingsBuilder<RunnerAndConfigurationSettings>() {
              public Collection<SettingsEditor<RunnerAndConfigurationSettings>> getEditors() {
                return myRunnerEditors;
              }

              public JComponent createCompoundEditor() {
                return myRunnersComponent.getComponent();
              }
            };
          }
        });
      }
    }
  }

  private JComponent createCompositePerRunnerSettings(final JavaProgramRunner runner) {
    final SettingsEditor<JDOMExternalizable> configEditor = myConfiguration.getRunnerSettingsEditor(runner);
    SettingsEditor<JDOMExternalizable> runnerEditor;

    try {
      runnerEditor = runner.getSettingsEditor(myConfiguration);
    } catch(AbstractMethodError error) {
      // this is stub code for plugin copatibility!
      runnerEditor = null;
    }

    if (configEditor == null && runnerEditor == null) return null;
    SettingsEditor<RunnerAndConfigurationSettings> wrappedConfigEditor = null;
    SettingsEditor<RunnerAndConfigurationSettings> wrappedRunEditor = null;
    if (configEditor != null) {
      wrappedConfigEditor = new SettingsEditorWrapper<RunnerAndConfigurationSettings, JDOMExternalizable>(configEditor, new Convertor<RunnerAndConfigurationSettings,
          JDOMExternalizable>() {
        public JDOMExternalizable convert(RunnerAndConfigurationSettings configurationSettings) {
          return configurationSettings.getConfigurationSettings(runner).getSettings();
        }
      });
      myRunnerEditors.add(wrappedConfigEditor);
    }

    if (runnerEditor != null) {
      wrappedRunEditor = new SettingsEditorWrapper<RunnerAndConfigurationSettings, JDOMExternalizable>(runnerEditor, new Convertor<RunnerAndConfigurationSettings,
          JDOMExternalizable>(){
        public JDOMExternalizable convert(RunnerAndConfigurationSettings configurationSettings) {
          return configurationSettings.getRunnerSettings(runner).getData();
        }
      });
      myRunnerEditors.add(wrappedRunEditor);
    }

    if (wrappedRunEditor != null && wrappedConfigEditor != null) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(wrappedConfigEditor.getComponent(), BorderLayout.CENTER);
      panel.add(wrappedRunEditor.getComponent(), BorderLayout.SOUTH);
      return panel;
    }

    if (wrappedRunEditor != null) return wrappedRunEditor.getComponent();
    return wrappedConfigEditor.getComponent();
  }

  public ConfigurationSettingsEditor(RunnerAndConfigurationSettings settings) {
    super(settings.createFactory());
    myConfigurationEditor = (SettingsEditor<RunConfiguration>)settings.getConfiguration().getConfigurationEditor();
    mySettings = settings;
    myConfiguration = mySettings.getConfiguration();
  }

  private static class RunnersEditorComponent {
    private static final String NO_RUNNER_COMPONENT = "<NO RUNNER LABEL>";

    private JList myRunnersList;
    private JPanel myRunnerPanel;
    private final CardLayout myLayout = new CardLayout();
    private final DefaultListModel myListModel = new DefaultListModel();
    private final JLabel myNoRunner = new JLabel("No runner selected");
    private JPanel myRunnersPanel;

    public RunnersEditorComponent() {
      myRunnerPanel.setLayout(myLayout);
      myRunnerPanel.add(myNoRunner, NO_RUNNER_COMPONENT);
      myRunnersList.setModel(myListModel);
      myRunnersList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          updateRunnerComponent();
        }
      });
      updateRunnerComponent();
      myRunnersList.setCellRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          JavaProgramRunner runner = (JavaProgramRunner)value;
          RunnerInfo info = runner.getInfo();
          setIcon(info.getIcon());
          append(info.getId(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      });
    }

    private void updateRunnerComponent() {
      JavaProgramRunner runner = (JavaProgramRunner)myRunnersList.getSelectedValue();
      myLayout.show(myRunnerPanel, runner != null ? runner.getInfo().getId() : NO_RUNNER_COMPONENT);
      myRunnersPanel.revalidate();
    }

    public void addRunnerComponent(JavaProgramRunner runner, JComponent component) {
      myRunnerPanel.add(component, runner.getInfo().getId());
      myListModel.addElement(runner);
      ListScrollingUtil.ensureSelectionExists(myRunnersList);
    }

    public JComponent getComponent() {
      return myRunnersPanel;
    }
  }

  private class ConfigToSettingsWrapper extends SettingsEditor<RunnerAndConfigurationSettings> {
    private final SettingsEditor<RunConfiguration> myConfigEditor;

    public ConfigToSettingsWrapper(SettingsEditor<RunConfiguration> configEditor) {
      myConfigEditor = configEditor;
    }

    public void resetEditorFrom(RunnerAndConfigurationSettings configurationSettings) {
      myConfigEditor.resetFrom(configurationSettings.getConfiguration());
    }

    public void applyEditorTo(RunnerAndConfigurationSettings configurationSettings) throws ConfigurationException {
      myConfigEditor.applyTo(configurationSettings.getConfiguration());
    }

    public JComponent createEditor() {
      return myConfigEditor.getComponent();
    }

    public void disposeEditor() {
      myConfigEditor.dispose();
    }
  }
}
