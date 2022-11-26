// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.internal.statistic.StructuredIdeActivity;
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
import org.jetbrains.idea.maven.statistics.MavenImportCollector;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.*;

import static org.jetbrains.idea.maven.importing.StandardMavenModuleType.*;

public class MavenProjectTreeLegacyImporter extends MavenProjectImporterLegacyBase {
  private static final Logger LOG = Logger.getInstance(MavenProjectTreeLegacyImporter.class);

  private volatile MavenModuleImportContext myContext;

  public MavenProjectTreeLegacyImporter(@NotNull Project p,
                                        @NotNull MavenProjectsTree projectsTree,
                                        @NotNull Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges,
                                        @NotNull IdeModifiableModelsProvider modelsProvider,
                                        @NotNull MavenImportingSettings importingSettings) {
    super(p, projectsTree, importingSettings, projectsToImportWithChanges, modelsProvider);

    myContext = new MavenModuleImportContext();
  }

  @Nullable
  @Override
  public List<MavenProjectsProcessorTask> importProject() {
    LegacyMavenProjectImportContextProvider contextProvider =
      new LegacyMavenProjectImportContextProvider(myProject, myProjectsTree, myModuleModel, myImportingSettings);

    Map<MavenProject, MavenProjectChanges> allProjectsWithChanges = new HashMap<>();

    StructuredIdeActivity activity = MavenImportCollector.LEGACY_IMPORT.started(myProject);

    for (var each : myProjectsToImportWithChanges.entrySet()) {
      if (!myProjectsTree.isIgnored(each.getKey())) {
        allProjectsWithChanges.put(each.getKey(), each.getValue());
      }
    }
    for (MavenProject eachProject : myProjectsTree.getProjects()) {
      if (!myProjectsTree.isIgnored(eachProject) && !allProjectsWithChanges.containsKey(eachProject)) {
        allProjectsWithChanges.put(eachProject, MavenProjectChanges.NONE);
      }
    }

    myContext = contextProvider.getContext(allProjectsWithChanges);
    boolean hasChanges = false;
    List<MavenProjectsProcessorTask> postTasks = new ArrayList<>();

    if (myProject.isDisposed()) return null;

    final List<MavenLegacyModuleImporter.ExtensionImporter> extensionImporters = new ArrayList<>();
    if (myContext.hasChanges) {
      hasChanges = true;
      StructuredIdeActivity createModulesPhase = MavenImportCollector.LEGACY_CREATE_MODULES_PHASE.startedWithParent(myProject, activity);

      TreeModuleConfigurer configurer = new TreeModuleConfigurer(myProjectsTree, myImportingSettings, myModelsProvider);
      extensionImporters.addAll(configurer.configModules(myContext.allModules, myContext.moduleNameByProject));
      scheduleRefreshResolvedArtifacts(postTasks, myProjectsToImportWithChanges.keySet());

      createModulesPhase.finished();
    }

    List<Module> obsoleteModules = myContext.legacyObsoleteModules;
    boolean isDeleteObsoleteModules = isDeleteObsoleteModules(obsoleteModules);
    hasChanges |= isDeleteObsoleteModules;

    if (myProject.isDisposed()) return null;
    if (hasChanges) {
      StructuredIdeActivity deleteObsoletePhase = MavenImportCollector.LEGACY_DELETE_OBSOLETE_PHASE.startedWithParent(myProject, activity);
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
      deleteObsoletePhase.finished();

      StructuredIdeActivity importersPhase = MavenImportCollector.LEGACY_IMPORTERS_PHASE.startedWithParent(myProject, activity);
      importExtensions(myProject, myIdeModifiableModelsProvider, extensionImporters, postTasks, importersPhase);
      importersPhase.finished();
    }
    else {
      finalizeImport(obsoleteModules);
    }

    activity.finished(() -> List.of(MavenImportCollector.NUMBER_OF_MODULES.with(myContext.allModules.size())));


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

    public List<MavenLegacyModuleImporter.ExtensionImporter> configModules(List<MavenTreeModuleImportData> allModules,
                                                                           Map<MavenProject, String> moduleNameByProject) {
      List<MavenLegacyModuleImporter.ExtensionImporter> extensionImporters = new ArrayList<>();

      for (MavenTreeModuleImportData importData : allModules) {
        MavenLegacyModuleImporter moduleImporter = createModuleImporter(importData, moduleNameByProject);
        ContainerUtil.addIfNotNull(extensionImporters, createExtensionImporterIfApplicable(importData, moduleNameByProject));

        if (!importData.getChanges().hasChanges()) continue;

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

      return extensionImporters;
    }

    private static void configModule(@NotNull MavenTreeModuleImportData importData,
                                     @NotNull MavenLegacyModuleImporter moduleImporter,
                                     @NotNull MavenRootModelAdapter rootModelAdapter) {
      StandardMavenModuleType type = importData.getModuleData().getType();
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
                                           moduleNameByProject,
                                           myImportingSettings,
                                           myModelsProvider);
    }

    @Nullable
    private MavenLegacyModuleImporter.ExtensionImporter createExtensionImporterIfApplicable(MavenTreeModuleImportData importData,
                                                                                            Map<MavenProject, String> moduleNameByProject) {
      return MavenLegacyModuleImporter.ExtensionImporter.createIfApplicable(importData.getMavenProject(),
                                                                            importData.getLegacyModuleData().getModule(),
                                                                            importData.getModuleData().getType(),
                                                                            myProjectsTree,
                                                                            importData.getChanges(),
                                                                            moduleNameByProject,
                                                                            false);
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

