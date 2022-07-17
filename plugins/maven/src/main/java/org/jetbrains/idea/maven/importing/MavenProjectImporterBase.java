// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.externalSystem.service.project.IdeUIModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenArtifactUtilKt;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

import java.io.File;
import java.util.*;

public abstract class MavenProjectImporterBase implements MavenProjectImporter {
  protected final Project myProject;

  protected final MavenProjectsTree myProjectsTree;
  protected final MavenImportingSettings myImportingSettings;

  protected final IdeModifiableModelsProvider myIdeModifiableModelsProvider;
  protected final ModifiableModelsProviderProxy myModelsProvider;

  public MavenProjectImporterBase(Project project,
                                  MavenProjectsTree projectsTree,
                                  MavenImportingSettings importingSettings,
                                  @NotNull IdeModifiableModelsProvider modelsProvider) {
    myProject = project;

    myProjectsTree = projectsTree;
    myImportingSettings = importingSettings;

    myIdeModifiableModelsProvider = modelsProvider;

    if (MavenUtil.newModelEnabled(myProject) && modelsProvider instanceof IdeModifiableModelsProviderImpl) {
      myModelsProvider =
        new ModifiableModelsProviderProxyImpl(myProject, ((IdeModifiableModelsProviderImpl)modelsProvider).getActualStorageBuilder());
    }
    else {
      myModelsProvider = new ModifiableModelsProviderProxyWrapper(myIdeModifiableModelsProvider);
    }
  }

  protected Set<MavenProject> selectProjectsToImport(Collection<MavenProject> originalProjects) {
    Set<MavenProject> result = new HashSet<>();
    for (MavenProject each : originalProjects) {
      if (!shouldCreateModuleFor(each)) continue;
      result.add(each);
    }
    return result;
  }

  protected boolean shouldCreateModuleFor(MavenProject project) {
    if (myProjectsTree.isIgnored(project)) return false;
    return !project.isAggregator() || myImportingSettings.isCreateModulesForAggregators();
  }

  protected void configFacets(List<MavenLegacyModuleImporter> importers, List<MavenProjectsProcessorTask> postTasks) {
    if (!importers.isEmpty()) {
      IdeModifiableModelsProvider provider;
      if (myIdeModifiableModelsProvider instanceof IdeUIModifiableModelsProvider) {
        provider = myIdeModifiableModelsProvider; // commit does nothing for this provider, so it should be reused
      }
      else {
        provider = ProjectDataManager.getInstance().createModifiableModelsProvider(myProject);
      }

      try {
        List<MavenLegacyModuleImporter> toRun =
          ContainerUtil.filter(importers, it -> !it.isModuleDisposed() && !it.isAggregatorMainTestModule());

        toRun.forEach(importer -> importer.setModifiableModelsProvider(provider));
        toRun.forEach(importer -> importer.preConfigFacets());
        toRun.forEach(importer -> importer.configFacets(postTasks));
        toRun.forEach(importer -> importer.postConfigFacets());
      }
      finally {
        MavenUtil.invokeAndWaitWriteAction(myProject, () -> {
          ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
            provider.commit();
          });
        });
      }
    }
  }

  protected void scheduleRefreshResolvedArtifacts(List<MavenProjectsProcessorTask> postTasks, Set<MavenProject> projectsToRefresh) {
    // We have to refresh all the resolved artifacts manually in order to
    // update all the VirtualFilePointers. It is not enough to call
    // VirtualFileManager.refresh() since the newly created files will be only
    // picked by FS when FileWatcher finishes its work. And in the case of import
    // it doesn't finish in time.
    // I couldn't manage to write a test for this since behaviour of VirtualFileManager
    // and FileWatcher differs from real-life execution.

    List<MavenArtifact> artifacts = new ArrayList<>();
    for (MavenProject each : projectsToRefresh) {
      artifacts.addAll(each.getDependencies());
    }

    final Set<File> files = new HashSet<>();
    for (MavenArtifact each : artifacts) {
      if (MavenArtifactUtilKt.resolved(each)) files.add(each.getFile());
    }

    if (MavenUtil.isMavenUnitTestModeEnabled()) {
      doRefreshFiles(files);
    }
    else {
      postTasks.add(new RefreshingFilesTask(files));
    }
  }

  protected void removeOutdatedCompilerConfigSettings() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    final JpsJavaCompilerOptions javacOptions = JavacConfiguration.getOptions(myProject, JavacConfiguration.class);
    String options = javacOptions.ADDITIONAL_OPTIONS_STRING;
    options = options.replaceFirst("(-target (\\S+))", ""); // Old IDEAs saved
    javacOptions.ADDITIONAL_OPTIONS_STRING = options;
  }


  protected static void doRefreshFiles(Set<File> files) {
    LocalFileSystem.getInstance().refreshIoFiles(files);
  }

  protected static class RefreshingFilesTask implements MavenProjectsProcessorTask {
    private final Set<File> myFiles;

    protected RefreshingFilesTask(Set<File> files) {
      myFiles = files;
    }

    @Override
    public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator) {
      indicator.setText(MavenProjectBundle.message("progress.text.refreshing.files"));
      doRefreshFiles(myFiles);
    }
  }
}
