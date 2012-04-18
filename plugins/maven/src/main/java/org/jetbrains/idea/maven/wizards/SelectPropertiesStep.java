/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenPropertiesPanel;
import org.jetbrains.idea.maven.indices.MavenIndex;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenEnvironmentForm;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class SelectPropertiesStep extends ModuleWizardStep {

  private final Project myProjectOrNull;
  private final MavenModuleBuilder myBuilder;

  private JPanel myMainPanel;
  private JPanel myEnvironmentPanel;
  private JPanel myPropertiesPanel;

  private MavenEnvironmentForm myEnvironmentForm;
  private MavenPropertiesPanel myMavenPropertiesPanel;

  private Map<String, String> myAvailableProperties = new HashMap<String, String>();

  public SelectPropertiesStep(@Nullable Project project, MavenModuleBuilder builder) {
    myProjectOrNull = project;
    myBuilder = builder;

    initComponents();
  }

  private void initComponents() {
    myEnvironmentForm = new MavenEnvironmentForm();

    Project project = myProjectOrNull == null ? ProjectManager.getInstance().getDefaultProject() : myProjectOrNull;
    myEnvironmentForm.getData(MavenProjectsManager.getInstance(project).getGeneralSettings().clone());

    myEnvironmentPanel.add(myEnvironmentForm.createComponent(), BorderLayout.CENTER);

    myMavenPropertiesPanel = new MavenPropertiesPanel(myAvailableProperties);
    myPropertiesPanel.add(myMavenPropertiesPanel);
  }

  @Override
  public void updateStep() {
    MavenArchetype archetype = myBuilder.getArchetype();

    Map<String, String> props = new LinkedHashMap<String, String>();

    MavenId projectId = myBuilder.getProjectId();

    props.put("groupId", projectId.getGroupId());
    props.put("artifactId", projectId.getArtifactId());
    props.put("version", projectId.getVersion());

    props.put("archetypeGroupId", archetype.groupId);
    props.put("archetypeArtifactId", archetype.artifactId);
    props.put("archetypeVersion", archetype.version);
    if (archetype.repository != null) props.put("archetypeRepository", archetype.repository);

    myMavenPropertiesPanel.setDataFromMap(props);
  }

  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isStepVisible() {
    return myBuilder.getArchetype() != null;
  }

  @Override
  public void updateDataModel() {
    myBuilder.setEnvironmentForm(myEnvironmentForm);
    myBuilder.setPropertiesToCreateByArtifact(myMavenPropertiesPanel.getDataAsMap());
  }
}
