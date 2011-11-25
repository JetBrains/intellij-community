/* ==========================================================================
 * Copyright 2006 Mevenide Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * =========================================================================
 */

package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.AddEditRemovePanel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.ComboBoxUtil;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public abstract class MavenRunnerConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final Project myProject;
  private final boolean myRunConfigurationMode;

  private JCheckBox myRunInBackgroundCheckbox;
  private RawCommandLineEditor myVMParametersEditor;
  private JComboBox myJdkCombo;
  private final DefaultComboBoxModel myJdkComboModel = new DefaultComboBoxModel();
  private JCheckBox mySkipTestsCheckBox;
  private MyPropertiesPanel myPropertiesPanel;

  private Map<String, String> myProperties;

  public MavenRunnerConfigurable(Project p, boolean isRunConfiguration) {
    myProject = p;
    myRunConfigurationMode = isRunConfiguration;
  }

  protected abstract MavenRunnerSettings getState();

  public JComponent createComponent() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.WEST;
    c.insets.bottom = 5;

    myRunInBackgroundCheckbox = new JCheckBox("Run in background");
    myRunInBackgroundCheckbox.setMnemonic('b');
    if (!myRunConfigurationMode) {
      c.gridx = 0;
      c.gridy++;
      c.weightx = 1;
      c.gridwidth = GridBagConstraints.REMAINDER;

      panel.add(myRunInBackgroundCheckbox, c);
    }
    c.gridwidth = 1;

    JLabel labelVMParameters = new JLabel("VM Options:");
    labelVMParameters.setDisplayedMnemonic('v');
    labelVMParameters.setLabelFor(myVMParametersEditor = new RawCommandLineEditor());
    myVMParametersEditor.setDialogCaption(labelVMParameters.getText());

    c.gridx = 0;
    c.gridy++;
    c.weightx = 0;
    panel.add(labelVMParameters, c);

    c.gridx = 1;
    c.weightx = 1;
    c.insets.left = 10;
    panel.add(myVMParametersEditor, c);
    c.insets.left = 0;

    JLabel jdkLabel = new JLabel("JRE:");
    jdkLabel.setDisplayedMnemonic('j');
    jdkLabel.setLabelFor(myJdkCombo = new JComboBox());
    c.gridx = 0;
    c.gridy++;
    c.weightx = 0;
    panel.add(jdkLabel, c);
    c.gridx = 1;
    c.weightx = 1;
    c.fill = GridBagConstraints.NONE;
    c.insets.left = 10;
    panel.add(myJdkCombo, c);
    c.insets.left = 0;
    c.fill = GridBagConstraints.HORIZONTAL;

    JPanel propertiesPanel = new JPanel(new BorderLayout());
    propertiesPanel.setBorder(IdeBorderFactory.createTitledBorder("Properties", false, false, true));

    propertiesPanel.add(mySkipTestsCheckBox = new JCheckBox("Skip tests"), BorderLayout.NORTH);
    mySkipTestsCheckBox.setMnemonic('t');
    propertiesPanel.add(myPropertiesPanel = new MyPropertiesPanel(), BorderLayout.CENTER);
    myPropertiesPanel.getEmptyText().setText("No properties defined");

    c.gridx = 0;
    c.gridy++;
    c.weightx = c.weighty = 1;
    c.gridwidth = c.gridheight = GridBagConstraints.REMAINDER;
    c.fill = GridBagConstraints.BOTH;
    panel.add(propertiesPanel, c);

    collectProperties();

    return panel;
  }

  private void collectProperties() {
    MavenProjectsManager s = MavenProjectsManager.getInstance(myProject);
    Map<String, String> result = new LinkedHashMap<String, String>();

    for (MavenProject each : s.getProjects()) {
      Properties properties = each.getProperties();
      for (Map.Entry p : properties.entrySet()) {
        result.put((String)p.getKey(), (String)p.getValue());
      }
    }

    myProperties = result;
  }

  public boolean isModified() {
    MavenRunnerSettings s = new MavenRunnerSettings();
    setData(s);
    return !s.equals(getState());
  }

  public void apply() throws ConfigurationException {
    setData(getState());
  }

  public void reset() {
    getData(getState());
  }

  @Nls
  public String getDisplayName() {
    return RunnerBundle.message("maven.tab.runner");
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settings.project.maven.runner";
  }

  @NotNull
  public String getId() {
    //noinspection ConstantConditions
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public void disposeUIResources() {

  }

  private void fillComboboxJdk(MavenRunnerSettings data) {
    myJdkComboModel.removeAllElements();
    for (Pair<String, String> jdk : data.collectJdkNamesAndDescriptions()) {
      ComboBoxUtil.addToModel(myJdkComboModel, jdk.getFirst(), jdk.getSecond());
    }
    myJdkCombo.setModel(myJdkComboModel);
  }

  void getData(MavenRunnerSettings data) {
    myRunInBackgroundCheckbox.setSelected(data.isRunMavenInBackground());
    myVMParametersEditor.setText(data.getVmOptions());
    mySkipTestsCheckBox.setSelected(data.isSkipTests());

    fillComboboxJdk(data);
    ComboBoxUtil.select(myJdkComboModel, data.getJreName());

    myPropertiesPanel.setDataFromMap(data.getMavenProperties());
  }

  void setData(MavenRunnerSettings data) {
    data.setRunMavenInBackground(myRunInBackgroundCheckbox.isSelected());
    data.setVmOptions(myVMParametersEditor.getText().trim());
    data.setSkipTests(mySkipTestsCheckBox.isSelected());
    data.setJreName(ComboBoxUtil.getSelectedString(myJdkComboModel));

    data.setMavenProperties(myPropertiesPanel.getDataAsMap());
  }

  private class MyPropertiesPanel extends AddEditRemovePanel<Pair<String, String>> {
    public MyPropertiesPanel() {
      super(new MyPropertiesTableModel(), new ArrayList<Pair<String, String>>(), null);
      setPreferredSize(new Dimension(100, 100));
    }

    protected Pair<String, String> addItem() {
      return doAddOrEdit(new Pair<String, String>("", ""));
    }

    protected boolean removeItem(Pair<String, String> o) {
      return true;
    }

    protected Pair<String, String> editItem(Pair<String, String> o) {
      return doAddOrEdit(o);
    }

    private Pair<String, String> doAddOrEdit(Pair<String, String> o) {
      EditMavenPropertyDialog d = new EditMavenPropertyDialog(myProject, o, myProperties);
      d.show();
      if (!d.isOK()) return null;
      return d.getValue();
    }

    public Map<String, String> getDataAsMap() {
      Map<String, String> result = new LinkedHashMap<String, String>();
      for (Pair<String, String> p : getData()) {
        result.put(p.getFirst(), p.getSecond());
      }
      return result;
    }

    public void setDataFromMap(Map<String, String> map) {
      List<Pair<String, String>> result = new ArrayList<Pair<String, String>>();
      for (Map.Entry<String, String> e : map.entrySet()) {
        result.add(new Pair<String, String>(e.getKey(), e.getValue()));
      }
      setData(result);
    }
  }

  private static class MyPropertiesTableModel extends AddEditRemovePanel.TableModel<Pair<String, String>> {
    public int getColumnCount() {
      return 2;
    }

    public String getColumnName(int c) {
      return c == 0 ? "Name" : "Value";
    }

    public Object getField(Pair<String, String> o, int c) {
      return c == 0 ? o.getFirst() : o.getSecond();
    }
  }
}
