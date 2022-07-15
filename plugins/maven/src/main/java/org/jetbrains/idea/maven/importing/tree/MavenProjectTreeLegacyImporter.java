// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.*;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.*;

import static org.jetbrains.idea.maven.importing.MavenModuleType.*;

public class MavenProjectTreeLegacyImporter extends MavenProjectImporterLegacyBase {
  private static final Logger LOG = Logger.getInstance(MavenProjectTreeLegacyImporter.class);

  private final LegacyMavenProjectImportContextProvider contextProvider;
  private volatile MavenModuleImportContext myContext;

  public MavenProjectTreeLegacyImporter(@NotNull Project p,
                                        @NotNull MavenProjectsTree projectsTree,
                                        @NotNull Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges,
                                        @NotNull IdeModifiableModelsProvider modelsProvider,
                                        @NotNull MavenImportingSettings importingSettings) {
    super(p, projectsTree, importingSettings, projectsToImportWithChanges, modelsProvider);

    myContext = new MavenModuleImportContext();
    contextProvider =
      new LegacyMavenProjectImportContextProvider(p, projectsTree, projectsToImportWithChanges, myModuleModel, importingSettings);
  }

  @Override
  @Nullable
  public List<MavenProjectsProcessorTask> importProject() {
    List<MavenProjectsProcessorTask> tasks = importProjectTree();
    return tasks;
  }

  @Nullable
  private List<MavenProjectsProcessorTask> importProjectTree() {
    myContext = contextProvider.getContext(ContainerUtil.filter(myProjectsTree.getProjects(), it -> !myProjectsTree.isIgnored(it)));
    boolean hasChanges = false;
    List<MavenProjectsProcessorTask> postTasks = new ArrayList<>();

    if (myProject.isDisposed()) return null;

    final List<MavenLegacyModuleImporter> importers = new ArrayList<>();
    if (myContext.hasChanges) {
      hasChanges = true;

      TreeModuleConfigurer configurer = new TreeModuleConfigurer(myProjectsTree, myImportingSettings, myModelsProvider);
      importers.addAll(configurer.configModules(myContext.allModules, myContext.moduleNameByProject));
      scheduleRefreshResolvedArtifacts(postTasks, myProjectsToImportWithChanges.keySet());
    }

    List<Module> obsoleteModules = myContext.legacyObsoleteModules;
    boolean isDeleteObsoleteModules = isDeleteObsoleteModules(obsoleteModules);
    hasChanges |= isDeleteObsoleteModules;

    if (myProject.isDisposed()) return null;
    if (hasChanges) {
      MavenUtil.invokeAndWaitWriteAction(myProject, () -> {
        ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
          setMavenizedModules(obsoleteModules, false);
          if (isDeleteObsoleteModules) {
            deleteModules(obsoleteModules);
          }
          removeUnusedProjectLibraries(myContext.changedModules);

          myModelsProvider.commit();

          if (myContext.hasChanges) {
            removeOutdatedCompilerConfigSettings(myProject);
            setMavenizedModules(ContainerUtil.map(myContext.changedModules, e -> e.getLegacyModuleData().getModule()), true);
          }
        });
      });

      configFacets(importers, postTasks, true);
    }
    else {
      finalizeImport(obsoleteModules);
    }

    return postTasks;
  }

  private static boolean isDeleteObsoleteModules(@NotNull List<Module> obsoleteModules) {
    return !obsoleteModules.isEmpty();
  }


  private void deleteModules(@NotNull List<Module> modules) {
    for (Module each : modules) {
      if (!each.isDisposed()) {
        myModuleModel.disposeModule(each);
      }
    }
  }

  public static class TreeModuleConfigurer {
    private final MavenProjectsTree myProjectsTree;
    private final MavenImportingSettings myImportingSettings;
    private final ModifiableModelsProviderProxy myModelsProvider;

    public TreeModuleConfigurer(MavenProjectsTree projectsTree,
                                MavenImportingSettings importingSettings,
                                ModifiableModelsProviderProxy modelsProvider) {
      myProjectsTree = projectsTree;
      myImportingSettings = importingSettings;
      myModelsProvider = modelsProvider;
    }

    public List<MavenLegacyModuleImporter> configModules(List<MavenTreeModuleImportData> allModules,
                                                         Map<MavenProject, String> moduleNameByProject) {
      List<MavenLegacyModuleImporter> importers = new ArrayList<>();

      for (MavenTreeModuleImportData importData : allModules) {
        MavenLegacyModuleImporter moduleImporter = createModuleImporter(importData, moduleNameByProject);
        importers.add(moduleImporter);

        if (!importData.hasChanges()) continue;

        MavenProject mavenProject = importData.getMavenProject();
        MavenId mavenId = mavenProject.getMavenId();
        Module module = importData.getLegacyModuleData().getModule();
        myModelsProvider.registerModulePublication(
          module, new ProjectId(mavenId.getGroupId(), mavenId.getArtifactId(), mavenId.getVersion())
        );

        MavenRootModelAdapterLegacyImpl delegate = new MavenRootModelAdapterLegacyImpl(mavenProject, module, myModelsProvider);
        MavenRootModelAdapter rootModelAdapter = new MavenRootModelAdapter(delegate);
        configModule(importData, moduleImporter, rootModelAdapter);
      }

      return importers;
    }

    private static void configModule(@NotNull MavenTreeModuleImportData importData,
                                     @NotNull MavenLegacyModuleImporter moduleImporter,
                                     @NotNull MavenRootModelAdapter rootModelAdapter) {
      MavenModuleType type = importData.getModuleData().getType();
      rootModelAdapter.init(importData.getLegacyModuleData().isNewModule());
      if (type == AGGREGATOR || type == SINGLE_MODULE) {
        moduleImporter.config(rootModelAdapter, importData);
      }
      else if (type == COMPOUND_MODULE) {
        moduleImporter.configMainAndTestAggregator(rootModelAdapter, importData);
      }
      else {
        moduleImporter.configMainAndTest(rootModelAdapter, importData);
      }
    }

    private MavenLegacyModuleImporter createModuleImporter(MavenTreeModuleImportData importData,
                                                           Map<MavenProject, String> moduleNameByProject) {
      return new MavenLegacyModuleImporter(importData.getLegacyModuleData().getModule(),
                                           myProjectsTree,
                                           importData.getMavenProject(),
                                           importData.getChanges(),
                                           moduleNameByProject,
                                           myImportingSettings,
                                           myModelsProvider,
                                           importData.getModuleData().getType());
    }
  }

  private void removeUnusedProjectLibraries(List<MavenTreeModuleImportData> changedModuleImportData) {
    Set<Library> unusedLibraries = new HashSet<>();
    Collections.addAll(unusedLibraries, myModelsProvider.getAllLibraries());

    for (ModuleRootModel eachModel : collectModuleModels(changedModuleImportData)) {
      for (OrderEntry eachEntry : eachModel.getOrderEntries()) {
        if (eachEntry instanceof LibraryOrderEntry) {
          unusedLibraries.remove(((LibraryOrderEntry)eachEntry).getLibrary());
        }
      }
    }

    for (Library each : unusedLibraries) {
      if (!isDisposed(each) && MavenRootModelAdapter.isMavenLibrary(each) && !MavenRootModelAdapter.isChangedByUser(each)) {
        myModelsProvider.removeLibrary(each);
      }
    }
  }

  private static boolean isDisposed(Library library) {
    return library instanceof LibraryEx && ((LibraryEx)library).isDisposed();
  }

  private Collection<ModuleRootModel> collectModuleModels(List<MavenTreeModuleImportData> changedModuleImportData) {
    Map<Module, ModuleRootModel> rootModels = new HashMap<>();
    for (MavenTreeModuleImportData importData : changedModuleImportData) {
      Module module = importData.getLegacyModuleData().getModule();
      ModifiableRootModel rootModel = myModelsProvider.getModifiableRootModel(module);
      rootModels.put(module, rootModel);
    }

    for (Module each : myModuleModel.getModules()) {
      if (rootModels.containsKey(each)) continue;
      rootModels.put(each, myModelsProvider.getModifiableRootModel(each));
    }
    return rootModels.values();
  }

  @Override
  public @NotNull List<Module> createdModules() {
    return myContext.legacyCreatedModules;
  }
}

