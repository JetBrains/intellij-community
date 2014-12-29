/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.navigator.SelectMavenProjectDialog;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class MavenModuleWizardStep extends ModuleWizardStep {
  private static final Icon WIZARD_ICON = null;

  private static final String INHERIT_GROUP_ID_KEY = "MavenModuleWizard.inheritGroupId";
  private static final String INHERIT_VERSION_KEY = "MavenModuleWizard.inheritVersion";
  private static final String ARCHETYPE_ARTIFACT_ID_KEY = "MavenModuleWizard.archetypeArtifactIdKey";
  private static final String ARCHETYPE_GROUP_ID_KEY = "MavenModuleWizard.archetypeGroupIdKey";
  private static final String ARCHETYPE_VERSION_KEY = "MavenModuleWizard.archetypeVersionKey";

  private final Project myProjectOrNull;
  private final MavenModuleBuilder myBuilder;
  private final WizardContext myContext;
  private MavenProject myAggregator;
  private MavenProject myParent;

  private String myInheritedGroupId;
  private String myInheritedVersion;

  private JPanel myMainPanel;

  private JLabel myAggregatorNameLabel;
  private JButton mySelectAggregator;
  private JLabel myParentNameLabel;
  private JButton mySelectParent;

  private JTextField myGroupIdField;
  private JCheckBox myInheritGroupIdCheckBox;
  private JTextField myArtifactIdField;
  private JTextField myVersionField;
  private JCheckBox myInheritVersionCheckBox;

  private JPanel myArchetypesPanel;
  private JPanel myAddToPanel;

  @Nullable
  private final MavenArchetypesStep myArchetypes;

  public MavenModuleWizardStep(MavenModuleBuilder builder, WizardContext context, boolean includeArtifacts) {
    myProjectOrNull = context.getProject();
    myBuilder = builder;
    myContext = context;
    if (includeArtifacts) {
      myArchetypes = new MavenArchetypesStep(builder, this);
      myArchetypesPanel.add(myArchetypes.getMainPanel(), BorderLayout.CENTER);
    }
    else {
      myArchetypes = null;
    }
    initComponents();
    loadSettings();
  }

  private void initComponents() {

    mySelectAggregator.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myAggregator = doSelectProject(myAggregator);
        updateComponents();
      }
    });

    mySelectParent.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myParent = doSelectProject(myParent);
        updateComponents();
      }
    });

    ActionListener updatingListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateComponents();
      }
    };
    myInheritGroupIdCheckBox.addActionListener(updatingListener);
    myInheritVersionCheckBox.addActionListener(updatingListener);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGroupIdField;
  }

  private MavenProject doSelectProject(MavenProject current) {
    assert myProjectOrNull != null : "must not be called when creating a new project";

    SelectMavenProjectDialog d = new SelectMavenProjectDialog(myProjectOrNull, current);
    if (!d.showAndGet()) {
      return current;
    }
    return d.getResult();
  }

  @Override
  public void onStepLeaving() {
    saveSettings();
  }

  private void loadSettings() {
    myBuilder.setInheritedOptions(getSavedValue(INHERIT_GROUP_ID_KEY, true),
                                  getSavedValue(INHERIT_VERSION_KEY, true));

    String archGroupId = getSavedValue(ARCHETYPE_GROUP_ID_KEY, null);
    String archArtifactId = getSavedValue(ARCHETYPE_ARTIFACT_ID_KEY, null);
    String archVersion = getSavedValue(ARCHETYPE_VERSION_KEY, null);
    if (archGroupId == null || archArtifactId == null || archVersion == null) {
      myBuilder.setArchetype(null);
    }
    else {
      myBuilder.setArchetype(new MavenArchetype(archGroupId, archArtifactId, archVersion, null, null));
    }
  }

  private void saveSettings() {
    saveValue(INHERIT_GROUP_ID_KEY, myInheritGroupIdCheckBox.isSelected());
    saveValue(INHERIT_VERSION_KEY, myInheritVersionCheckBox.isSelected());

    if (myArchetypes != null) {
      MavenArchetype arch = myArchetypes.getSelectedArchetype();
      saveValue(ARCHETYPE_GROUP_ID_KEY, arch == null ? null : arch.groupId);
      saveValue(ARCHETYPE_ARTIFACT_ID_KEY, arch == null ? null : arch.artifactId);
      saveValue(ARCHETYPE_VERSION_KEY, arch == null ? null : arch.version);
    }
  }

  private static boolean getSavedValue(String key, boolean defaultValue) {
    return getSavedValue(key, String.valueOf(defaultValue)).equals(String.valueOf(true));
  }

  private static String getSavedValue(String key, String defaultValue) {
    String value = PropertiesComponent.getInstance().getValue(key);
    return value == null ? defaultValue : value;
  }

  private static void saveValue(String key, boolean value) {
    saveValue(key, String.valueOf(value));
  }

  private static void saveValue(String key, String value) {
    PropertiesComponent props = PropertiesComponent.getInstance();
    props.setValue(key, value);
  }

  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public boolean validate() throws ConfigurationException {
    if (StringUtil.isEmptyOrSpaces(myGroupIdField.getText())) {
      throw new ConfigurationException("Please, specify groupId");
    }

    if (StringUtil.isEmptyOrSpaces(myArtifactIdField.getText())) {
      throw new ConfigurationException("Please, specify artifactId");
    }

    if (StringUtil.isEmptyOrSpaces(myVersionField.getText())) {
      throw new ConfigurationException("Please, specify version");
    }

    return true;
  }

  public MavenProject findPotentialParentProject(Project project) {
    if (!MavenProjectsManager.getInstance(project).isMavenizedProject()) return null;

    VirtualFile parentPom = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myContext.getProjectFileDirectory(), "pom.xml"));
    if (parentPom == null) return null;

    return MavenProjectsManager.getInstance(project).findProject(parentPom);
  }

  private static void setTestIfEmpty(@NotNull JTextField artifactIdField, @Nullable String text) {
    if (StringUtil.isEmpty(artifactIdField.getText())) {
      artifactIdField.setText(StringUtil.notNullize(text));
    }
  }

  @Override
  public void updateStep() {
    if (myArchetypes != null && myArchetypes.isSkipUpdateUI()) return;

    if (isMavenizedProject()) {
      MavenProject parent = findPotentialParentProject(myProjectOrNull);
      myAggregator = parent;
      myParent = parent;
    }

    MavenId projectId = myBuilder.getProjectId();

    if (projectId == null) {
      setTestIfEmpty(myArtifactIdField, myBuilder.getName());
      setTestIfEmpty(myGroupIdField, myParent == null ? myBuilder.getName() : myParent.getMavenId().getGroupId());
      setTestIfEmpty(myVersionField, myParent == null ? "1.0-SNAPSHOT" : myParent.getMavenId().getVersion());
    }
    else {
      setTestIfEmpty(myArtifactIdField, projectId.getArtifactId());
      setTestIfEmpty(myGroupIdField, projectId.getGroupId());
      setTestIfEmpty(myVersionField, projectId.getVersion());
    }

    myInheritGroupIdCheckBox.setSelected(myBuilder.isInheritGroupId());
    myInheritVersionCheckBox.setSelected(myBuilder.isInheritVersion());

    if (myArchetypes != null) {
      myArchetypes.requestUpdate();
    }
    updateComponents();
  }

  private boolean isMavenizedProject() {
    return myProjectOrNull != null && MavenProjectsManager.getInstance(myProjectOrNull).isMavenizedProject();
  }

  private void updateComponents() {
    myAddToPanel.setVisible(isMavenizedProject());
    myAggregatorNameLabel.setText(formatProjectString(myAggregator));
    myParentNameLabel.setText(formatProjectString(myParent));

    if (myParent == null) {
      myGroupIdField.setEnabled(true);
      myVersionField.setEnabled(true);
      myInheritGroupIdCheckBox.setEnabled(false);
      myInheritVersionCheckBox.setEnabled(false);
    }
    else {
      myGroupIdField.setEnabled(!myInheritGroupIdCheckBox.isSelected());
      myVersionField.setEnabled(!myInheritVersionCheckBox.isSelected());

      if (myInheritGroupIdCheckBox.isSelected()
          || myGroupIdField.getText().equals(myInheritedGroupId)) {
        myGroupIdField.setText(myParent.getMavenId().getGroupId());
      }
      if (myInheritVersionCheckBox.isSelected()
          || myVersionField.getText().equals(myInheritedVersion)) {
        myVersionField.setText(myParent.getMavenId().getVersion());
      }
      myInheritedGroupId = myGroupIdField.getText();
      myInheritedVersion = myVersionField.getText();

      myInheritGroupIdCheckBox.setEnabled(true);
      myInheritVersionCheckBox.setEnabled(true);
    }
  }

  private static String formatProjectString(MavenProject project) {
    if (project == null) return "<none>";
    return project.getMavenId().getDisplayString();
  }

  @Override
  public void updateDataModel() {
    myContext.setProjectBuilder(myBuilder);
    myBuilder.setAggregatorProject(myAggregator);
    myBuilder.setParentProject(myParent);

    myBuilder.setProjectId(new MavenId(myGroupIdField.getText(),
                                       myArtifactIdField.getText(),
                                       myVersionField.getText()));
    myBuilder.setInheritedOptions(myInheritGroupIdCheckBox.isSelected(),
                                  myInheritVersionCheckBox.isSelected());

    if (myContext.getProjectName() == null) {
      myContext.setProjectName(myBuilder.getProjectId().getArtifactId());
    }

    if (myArchetypes != null) {
      myBuilder.setArchetype(myArchetypes.getSelectedArchetype());
    }
  }

  @Override
  public Icon getIcon() {
    return WIZARD_ICON;
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.new.project.fromScratch.maven";
  }

  @Override
  public void disposeUIResources() {
    if (myArchetypes != null) {
      Disposer.dispose(myArchetypes);
    }
  }
}

