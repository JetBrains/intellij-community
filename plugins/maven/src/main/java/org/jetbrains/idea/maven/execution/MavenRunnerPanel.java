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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.ComboBoxUtil;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class MavenRunnerPanel {
  protected final Project myProject;
  private final boolean myRunConfigurationMode;

  private JCheckBox myRunInBackgroundCheckbox;
  private RawCommandLineEditor myVMParametersEditor;
  private JComboBox myJdkCombo;
  private final DefaultComboBoxModel myJdkComboModel = new DefaultComboBoxModel();
  private JCheckBox mySkipTestsCheckBox;
  private MavenPropertiesPanel myPropertiesPanel;

  private Map<String, String> myProperties;

  public MavenRunnerPanel(@NotNull Project p, boolean isRunConfiguration) {
    myProject = p;
    myRunConfigurationMode = isRunConfiguration;
  }

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
    propertiesPanel.setBorder(IdeBorderFactory.createTitledBorder("Properties", false));

    propertiesPanel.add(mySkipTestsCheckBox = new JCheckBox("Skip tests"), BorderLayout.NORTH);
    mySkipTestsCheckBox.setMnemonic('t');

    collectProperties();
    propertiesPanel.add(myPropertiesPanel = new MavenPropertiesPanel(myProperties), BorderLayout.CENTER);
    myPropertiesPanel.getEmptyText().setText("No properties defined");

    c.gridx = 0;
    c.gridy++;
    c.weightx = c.weighty = 1;
    c.gridwidth = c.gridheight = GridBagConstraints.REMAINDER;
    c.fill = GridBagConstraints.BOTH;
    panel.add(propertiesPanel, c);

    return panel;
  }

  private void collectProperties() {
    MavenProjectsManager s = MavenProjectsManager.getInstance(myProject);
    Map<String, String> result = new LinkedHashMap<String, String>();

    for (MavenProject each : s.getProjects()) {
      Properties properties = each.getProperties();
      result.putAll((Map)properties);
    }

    myProperties = result;
  }

  protected void getData(MavenRunnerSettings data) {
    myRunInBackgroundCheckbox.setSelected(data.isRunMavenInBackground());
    myVMParametersEditor.setText(data.getVmOptions());
    mySkipTestsCheckBox.setSelected(data.isSkipTests());

    Map<String, String> jdkMap = collectJdkNamesAndDescriptions();
    if (!jdkMap.containsKey(data.getJreName())) {
      jdkMap.put(data.getJreName(), data.getJreName());
    }

    myJdkComboModel.removeAllElements();
    for (Map.Entry<String, String> entry : jdkMap.entrySet()) {
      ComboBoxUtil.addToModel(myJdkComboModel, entry.getKey(), entry.getValue());
    }
    myJdkCombo.setModel(myJdkComboModel);
    ComboBoxUtil.select(myJdkComboModel, data.getJreName());

    myPropertiesPanel.setDataFromMap(data.getMavenProperties());
  }

  private Map<String, String> collectJdkNamesAndDescriptions() {
    Map<String, String> result = new LinkedHashMap<String, String>();

    for (Sdk projectJdk : ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance())) {
      String name = projectJdk.getName();
      result.put(name, name);
    }

    result.put(MavenRunnerSettings.USE_INTERNAL_JAVA, RunnerBundle.message("maven.java.internal"));

    String projectJdkTitle;

    String projectJdk = ProjectRootManager.getInstance(myProject).getProjectSdkName();
    if (projectJdk == null) {
      projectJdkTitle = "Use Project JDK (not defined yet)";
    }
    else {
      projectJdkTitle = "Use Project JDK (" + projectJdk + ')';
    }

    result.put(MavenRunnerSettings.USE_PROJECT_JDK, projectJdkTitle);
    result.put(MavenRunnerSettings.USE_JAVA_HOME, RunnerBundle.message("maven.java.home.env"));

    return result;
  }

  protected void setData(MavenRunnerSettings data) {
    data.setRunMavenInBackground(myRunInBackgroundCheckbox.isSelected());
    data.setVmOptions(myVMParametersEditor.getText().trim());
    data.setSkipTests(mySkipTestsCheckBox.isSelected());
    data.setJreName(ComboBoxUtil.getSelectedString(myJdkComboModel));

    data.setMavenProperties(myPropertiesPanel.getDataAsMap());
  }

  public Project getProject() {
    return myProject;
  }
}
