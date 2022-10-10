// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeUIModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.statistics.MavenImportCollector;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

@ApiStatus.Internal
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
    myModelsProvider = new ModifiableModelsProviderProxyWrapper(myIdeModifiableModelsProvider);
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

  public static void importExtensions(Project project,
                                      IdeModifiableModelsProvider modifiableModelsProvider,
                                      List<MavenLegacyModuleImporter.ExtensionImporter> extensionImporters,
                                      List<MavenProjectsProcessorTask> postTasks,
                                      StructuredIdeActivity activity) {
    extensionImporters = ContainerUtil.filter(extensionImporters, it -> !it.isModuleDisposed());

    if (extensionImporters.isEmpty()) return;

    IdeModifiableModelsProvider provider;
    if (modifiableModelsProvider instanceof IdeUIModifiableModelsProvider) {
      provider = modifiableModelsProvider; // commit does nothing for this provider, so it should be reused
    }
    else {
      provider = ProjectDataManager.getInstance().createModifiableModelsProvider(project);
    }

    try {
      Map<Class<? extends MavenImporter>, MavenLegacyModuleImporter.ExtensionImporter.CountAndTime> counters = new HashMap<>();

      extensionImporters.forEach(importer -> importer.init(provider));
      extensionImporters.forEach(importer -> importer.preConfig(counters));
      extensionImporters.forEach(importer -> importer.config(postTasks, counters));
      extensionImporters.forEach(importer -> importer.postConfig(counters));

      for (Map.Entry<Class<? extends MavenImporter>, MavenLegacyModuleImporter.ExtensionImporter.CountAndTime> each : counters.entrySet()) {
        MavenImportCollector.IMPORTER_RUN.log(project,
                                              MavenImportCollector.ACTIVITY_ID.with(activity),
                                              MavenImportCollector.IMPORTER_CLASS.with(each.getKey()),
                                              MavenImportCollector.NUMBER_OF_MODULES.with(each.getValue().count),
                                              MavenImportCollector.TOTAL_DURATION_MS.with(
                                                TimeUnit.NANOSECONDS.toMillis(each.getValue().timeNano)));
      }
    }
    finally {
      MavenUtil.invokeAndWaitWriteAction(project, () -> {
        ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(() -> {
          provider.commit();
        });
      });
    }
  }

  public static void scheduleRefreshResolvedArtifacts(List<MavenProjectsProcessorTask> postTasks,
                                                      Iterable<MavenProject> projectsToRefresh) {
    // We have to refresh all the resolved artifacts manually in order to
    // update all the VirtualFilePointers. It is not enough to call
    // VirtualFileManager.refresh() since the newly created files will be only
    // picked by FS when FileWatcher finishes its work. And in the case of import
    // it doesn't finish in time.
    // I couldn't manage to write a test for this since behaviour of VirtualFileManager
    // and FileWatcher differs from real-life execution.

    HashSet<File> files = new HashSet<>();
    for (MavenProject project : projectsToRefresh) {
      for (MavenArtifact dependency : project.getDependencies()) {
        files.add(dependency.getFile());
      }
    }

    if (MavenUtil.isMavenUnitTestModeEnabled()) {
      doRefreshFiles(files);
    }
    else {
      postTasks.add(new RefreshingFilesTask(files));
    }
  }

  protected static void removeOutdatedCompilerConfigSettings(Project project) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    final JpsJavaCompilerOptions javacOptions = JavacConfiguration.getOptions(project, JavacConfiguration.class);
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
