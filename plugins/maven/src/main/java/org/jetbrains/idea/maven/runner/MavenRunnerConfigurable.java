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

package org.jetbrains.idea.maven.runner;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.AddEditRemovePanel;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.ComboBoxUtil;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectModel;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;

/**
 * Creates the form for the Maven2 build setup and is responsible for updating the
 * underlying data model.
 *
 * @author Ralf Quebbemann (ralfq@codehaus.org)
 */
public abstract class MavenRunnerConfigurable implements Configurable {
  private JPanel panel;
  private JCheckBox checkBoxRunMavenInBackground;
  private JRadioButton radioButtonUseEmbeddedMaven;
  private JRadioButton radioButtonUseExternalMaven;
  private JLabel labelVMParameters;
  private RawCommandLineEditor textFieldVMParameters;
  private JLabel labelJdkHomeDirectory;
  private JComboBox comboBoxChooseJDK;
  private final DefaultComboBoxModel comboboxModelChooseJdk = new DefaultComboBoxModel();
  private JCheckBox checkBoxSkipTests;
  private JPanel panelForPropertiesEditor;
  private MyPropertiesPanel propertiesPanel;
  private Project myProject;
  private Map<String, String> myProperties;

  public MavenRunnerConfigurable(Project p, boolean isRunConfiguration) {
    myProject = p;

    propertiesPanel = new MyPropertiesPanel();
    panelForPropertiesEditor.add(propertiesPanel);

    textFieldVMParameters.setDialogCaption(labelVMParameters.getText());

    if (isRunConfiguration) {
      checkBoxRunMavenInBackground.setVisible(false);
      radioButtonUseEmbeddedMaven.setVisible(false);
      radioButtonUseExternalMaven.setVisible(false);
    } else {
      final ItemListener mavenModeListener = new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          enableControls();
        }
      };

      radioButtonUseEmbeddedMaven.addItemListener(mavenModeListener);
      radioButtonUseExternalMaven.addItemListener(mavenModeListener);
    }

    collectProperties();
  }

  private void collectProperties() {
    MavenProjectsManager s = MavenProjectsManager.getInstance(myProject);
    Map<String, String> result = new LinkedHashMap<String, String>();

    for(MavenProjectModel each : s.getProjects()) {
      Properties properties = each.getProperties();
      for (Map.Entry p : properties.entrySet()) {
        result.put((String)p.getKey(), (String)p.getValue());
      }
    }

    myProperties = result;
  }

  protected abstract MavenRunnerSettings getState();

  public JComponent createComponent() {
    return getRootComponent();
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
    return null;
  }

  public void disposeUIResources() {

  }

  private void fillComboboxJdk(MavenRunnerSettings data) {
    comboboxModelChooseJdk.removeAllElements();
    for (Pair<String, String> jdk : data.collectJdkNamesAndDescriptions()) {
      ComboBoxUtil.addToModel(comboboxModelChooseJdk, jdk.getFirst(), jdk.getSecond());
    }
    comboBoxChooseJDK.setModel(comboboxModelChooseJdk);
  }

  JComponent getRootComponent() {
    return panel;
  }

  void getData(MavenRunnerSettings data) {
    radioButtonUseEmbeddedMaven.setSelected(data.isUseMavenEmbedder());
    radioButtonUseExternalMaven.setSelected(!data.isUseMavenEmbedder());
    checkBoxRunMavenInBackground.setSelected(data.isRunMavenInBackground());
    textFieldVMParameters.setText(data.getVmOptions());
    checkBoxSkipTests.setSelected(data.isSkipTests());

    enableControls();

    fillComboboxJdk(data);
    ComboBoxUtil.select(comboboxModelChooseJdk, data.getJreName());

    propertiesPanel.setDataFromMap(data.getMavenProperties());
  }

  void setData(MavenRunnerSettings data) {
    data.setUseMavenEmbedder(radioButtonUseEmbeddedMaven.isSelected());
    data.setRunMavenInBackground(checkBoxRunMavenInBackground.isSelected());
    data.setVmOptions(textFieldVMParameters.getText().trim());
    data.setSkipTests(checkBoxSkipTests.isSelected());
    data.setJreName(ComboBoxUtil.getSelectedString(comboboxModelChooseJdk));

    data.setMavenProperties(propertiesPanel.getDataAsMap());
  }

  private void enableControls() {
    final boolean on = radioButtonUseExternalMaven.isSelected();
    labelVMParameters.setEnabled(on);
    textFieldVMParameters.setEnabled(on);
    labelJdkHomeDirectory.setEnabled(on);
    comboBoxChooseJDK.setEnabled(on);
  }

  private class MyPropertiesPanel extends AddEditRemovePanel<Pair<String, String>> {
    public MyPropertiesPanel() {
      super(new MyPropertiesTableModel(), new ArrayList<Pair<String, String>>(), null);
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

  private static class MyPropertiesTableModel implements AddEditRemovePanel.TableModel<Pair<String, String>> {
    public int getColumnCount() {
      return 2;
    }

    public String getColumnName(int c) {
      return c == 0 ? "Name" : "Value";
    }

    public Object getField(Pair<String, String> o, int c) {
      return c == 0? o.getFirst() : o.getSecond();
    }
  }
}
