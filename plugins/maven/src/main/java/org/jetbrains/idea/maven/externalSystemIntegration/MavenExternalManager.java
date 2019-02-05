// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.SearchScopeProvider;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.run.MavenTaskManager;
import org.jetbrains.idea.maven.externalSystemIntegration.settings.*;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.Set;

public class MavenExternalManager implements ExternalSystemManager<
  MavenProjectSettings,
  MavenSettingsListener,
  MavenSettings,
  MavenLocalSettings,
  MavenExecutionSettings> {


  @NotNull
  @Override
  public ProjectSystemId getSystemId() {
    return MavenConstants.SYSTEM_ID;
  }

  @NotNull
  @Override
  public Function<Project, MavenSettings> getSettingsProvider() {
    return project -> MavenSettings.getInstance(project);
  }

  @NotNull
  @Override
  public Function<Project, MavenLocalSettings> getLocalSettingsProvider() {
    return project -> MavenLocalSettings.getInstance(project);
  }

  @NotNull
  @Override
  public Function<Pair<Project, String>, MavenExecutionSettings> getExecutionSettingsProvider() {
    return pair -> {
      Project project = pair.first;
      MavenSettings settings = MavenSettings.getInstance(project);
      File mavenHome = MavenServerManager.getMavenHomeFile(MavenServerManager.BUNDLED_MAVEN_3);
      MavenExecutionSettings mavenExecutionSettings = new MavenExecutionSettings(
        mavenHome, MavenUtil.getMavenVersion(mavenHome),
        ExternalSystemJdkUtil
          .getJdk(project, settings.getJdkToUse()).getHomePath(),
        LocalFileSystem.getInstance().findFileByIoFile(new File(pair.second)).findChild("pom.xml"));
      return mavenExecutionSettings;
    };
  }

  @NotNull
  @Override
  public Class<? extends ExternalSystemProjectResolver<MavenExecutionSettings>> getProjectResolverClass() {
    return MavenProjectResolver.class;
  }

  @Override
  public Class<? extends ExternalSystemTaskManager<MavenExecutionSettings>> getTaskManagerClass() {
    return MavenTaskManager.class;
  }

  @NotNull
  @Override
  public FileChooserDescriptor getExternalProjectDescriptor() {
    return new FileChooserDescriptor(true, false, false, false, false, false)
      .withTitle("Choose Maven File")
      .withFileFilter(MavenUtil::isPomFile);
  }

  @Override
  public void enhanceRemoteProcessing(@NotNull SimpleJavaParameters parameters) throws ExecutionException {
    final Set<String> additionalEntries = ContainerUtilRt.newHashSet();
    ContainerUtil.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(MavenServerManager.class));
    ContainerUtil.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(MavenConstants.class));

    final PathsList classPath = parameters.getClassPath();
    for (String entry : additionalEntries) {
      classPath.add(entry);
    }
  }

  @Nullable
  @Override
  public GlobalSearchScope getSearchScope(@NotNull Project project, @NotNull ExternalSystemTaskExecutionSettings taskExecutionSettings) {
    return  SearchScopeProvider.createSearchScope(project, null);
  }
}
