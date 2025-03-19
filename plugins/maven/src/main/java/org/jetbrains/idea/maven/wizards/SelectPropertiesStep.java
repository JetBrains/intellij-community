// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenPropertiesPanel;
import org.jetbrains.idea.maven.indices.MavenArchetypeManager;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenEnvironmentForm;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @deprecated use {@link org.jetbrains.idea.maven.wizards.archetype.MavenArchetypeNewProjectWizard} instead
 */
@Deprecated
@SuppressWarnings("DeprecatedIsStillUsed")
public class SelectPropertiesStep extends ModuleWizardStep {

  private final Project myProjectOrNull;
  private final AbstractMavenModuleBuilder myBuilder;

  private JPanel myMainPanel;
  private JPanel myEnvironmentPanel;
  private JPanel myPropertiesPanel;

  private MavenEnvironmentForm myEnvironmentForm;
  private MavenPropertiesPanel myMavenPropertiesPanel;

  private final Map<String, String> myAvailableProperties = new HashMap<>();

  public SelectPropertiesStep(@Nullable Project project, AbstractMavenModuleBuilder builder) {
    myProjectOrNull = project;
    myBuilder = builder;

    initComponents();
  }

  public SelectPropertiesStep(@Nullable Project project, MavenModuleBuilder builder) {
    this(project, (AbstractMavenModuleBuilder)builder);
  }

  private void initComponents() {
    myEnvironmentForm = new MavenEnvironmentForm();

    Project project = myProjectOrNull == null ? ProjectManager.getInstance().getDefaultProject() : myProjectOrNull;
    myEnvironmentForm.initializeFormData(MavenProjectsManager.getInstance(project).getGeneralSettings().clone(), project);

    myEnvironmentPanel.add(myEnvironmentForm.createComponent(), BorderLayout.CENTER);

    myMavenPropertiesPanel = new MavenPropertiesPanel(myAvailableProperties);
    myPropertiesPanel.add(myMavenPropertiesPanel);
  }

  @Override
  public void updateStep() {
    MavenArchetype archetype = myBuilder.getArchetype();

    MavenId projectId = myBuilder.getProjectId();

    Map<String, String> descriptor = getArchetypeDescriptor(archetype);

    Map<String, String> props = new LinkedHashMap<>();
    props.put("groupId", projectId.getGroupId());
    props.put("artifactId", projectId.getArtifactId());
    props.put("version", projectId.getVersion());

    props.put("archetypeGroupId", archetype.groupId);
    props.put("archetypeArtifactId", archetype.artifactId);
    props.put("archetypeVersion", archetype.version);

    props.putAll(descriptor);
    if (archetype.repository != null) props.put("archetypeRepository", archetype.repository);

    myMavenPropertiesPanel.setDataFromMap(props);
  }

  private @NotNull Map<String, String> getArchetypeDescriptor(MavenArchetype archetype) {
    if (myProjectOrNull == null) return Collections.emptyMap();
    Map<String, String> descriptor = MavenArchetypeManager.getInstance(myProjectOrNull)
      .resolveAndGetArchetypeDescriptor(archetype.groupId, archetype.artifactId, archetype.version, archetype.repository);
    return descriptor == null ? Collections.emptyMap() : descriptor;
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

  @Override
  public String getHelpId() {
    return "New_Projects_from_Scratch_Maven_Settings_Page";
  }
}
