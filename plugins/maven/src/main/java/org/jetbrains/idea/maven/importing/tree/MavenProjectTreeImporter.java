// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemModulePropertyManagerBridge;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.*;
import org.jetbrains.idea.maven.importing.configurers.MavenModuleConfigurer;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

import java.util.*;

import static org.jetbrains.idea.maven.importing.tree.MavenModuleType.*;

public class MavenProjectTreeImporter extends MavenProjectImporterBase {
  private static final Logger LOG = Logger.getInstance(MavenProjectTreeImporter.class);

  private final Project myProject;
  private final ModifiableModelsProviderProxy myModelsProvider;
  private final ModuleModelProxy myModuleModel;
  private final MavenProjectImportContextProvider contextProvider;

  private volatile MavenModuleImportContext myContext;

  public MavenProjectTreeImporter(@NotNull Project p,
                                  @NotNull MavenProjectsTree projectsTree,
                                  @NotNull Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges,
                                  @NotNull IdeModifiableModelsProvider modelsProvider,
                                  @NotNull MavenImportingSettings importingSettings) {
    super(projectsTree, importingSettings, projectsToImportWithChanges);
    myProject = p;

    myModelsProvider = getModelProvider(modelsProvider);
    myModuleModel = myModelsProvider.getModuleModelProxy();
    myContext = new MavenModuleImportContext();
    contextProvider = new MavenProjectImportContextProvider(p, projectsTree, projectsToImportWithChanges, myModuleModel, importingSettings);
  }

  private ModifiableModelsProviderProxy getModelProvider(IdeModifiableModelsProvider modelsProvider) {
    return (MavenUtil.newModelEnabled(myProject))
           ? new ModifiableModelsProviderProxyImpl(myProject, ((IdeModifiableModelsProviderImpl)modelsProvider).getActualStorageBuilder())
           : new ModifiableModelsProviderProxyWrapper(modelsProvider);
  }

  @Override
  @Nullable
  public List<MavenProjectsProcessorTask> importProject() {
    long startTime = System.currentTimeMillis();
    List<MavenProjectsProcessorTask> tasks = importProjectTree();
    LOG.info("[maven import] applying models took " + (System.currentTimeMillis() - startTime) + "ms");
    return tasks;
  }

  @Nullable
  private List<MavenProjectsProcessorTask> importProjectTree() {
    myContext = contextProvider.getContext();
    boolean hasChanges = false;
    List<MavenProjectsProcessorTask> postTasks = new ArrayList<>();

    if (myProject.isDisposed()) return null;

    final List<MavenModuleImporter> importers = new ArrayList<>();
    if (myContext.hasChanges) {
      hasChanges = true;
      importers.addAll(importModules(myContext));
      scheduleRefreshResolvedArtifacts(postTasks);
    }

    List<Module> obsoleteModules = myContext.obsoleteModules;
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
            removeOutdatedCompilerConfigSettings();
            setMavenizedModules(ContainerUtil.map(myContext.changedModules, e -> e.getModuleData().getModule()), true);
          }
        });
      });

      if (!importers.isEmpty()) {
        IdeModifiableModelsProvider provider = ProjectDataManager.getInstance().createModifiableModelsProvider(myProject);
        try {
          List<MavenModuleImporter> toRun = new ArrayList<>(importers.size());
          for (MavenModuleImporter importer : importers) {
            if (!importer.isModuleDisposed()) {
              importer.setModifiableModelsProvider(provider);
              toRun.add(importer);
            }
          }
          configFacets(postTasks, toRun);
        }
        finally {
          MavenUtil.invokeAndWaitWriteAction(myProject, () -> {
            ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
              provider.commit();
            });
          });
        }
      }

      configureMavenProjects(myContext);
    }
    else {
      MavenUtil.invokeAndWaitWriteAction(myProject, () -> setMavenizedModules(obsoleteModules, false));
      disposeModifiableModels();
    }

    return postTasks;
  }

  private boolean isDeleteObsoleteModules(@NotNull List<Module> obsoleteModules) {
    if (obsoleteModules.isEmpty()) {
      return false;
    }
    if (!ApplicationManager.getApplication().isHeadlessEnvironment() || MavenUtil.isMavenUnitTestModeEnabled()) {
      final int[] result = new int[1];
      MavenUtil.invokeAndWait(myProject, myModelsProvider.getModalityStateForQuestionDialogs(),
                              () -> result[0] = Messages.showYesNoDialog(myProject,
                                                                         MavenProjectBundle.message("maven.import.message.delete.obsolete",
                                                                                                    formatModules(obsoleteModules)),
                                                                         MavenProjectBundle.message("maven.project.import.title"),
                                                                         Messages.getQuestionIcon()));

      if (result[0] == Messages.NO) {
        return false;
      }
    }
    return true;
  }

  private static String formatModules(final Collection<Module> modules) {
    StringBuilder res = new StringBuilder();

    int i = 0;
    for (Module module : modules) {
      res.append('\'').append(module.getName()).append("'\n");

      if (++i > 20) break;
    }

    if (i > 20) {
      res.append("\n ... and other ").append(modules.size() - 20).append(" modules");
    }

    return res.toString();
  }

  private void configureMavenProjects(MavenModuleImportContext moduleImportContext) {
    List<MavenModuleConfigurer> configurers = MavenModuleConfigurer.getConfigurers();
    List<MavenModuleImportData> allModules = moduleImportContext.allModules;
    MavenUtil.runInBackground(myProject, MavenProjectBundle.message("command.name.configuring.projects"), false, indicator -> {
      float count = 0;
      long startTime = System.currentTimeMillis();
      int size = allModules.size();
      LOG.info("[maven import] applying " + configurers.size() + " configurers to " + size + " Maven projects");
      for (MavenModuleImportData importData : allModules) {
        Module module = importData.getModuleData().getModule();
        indicator.setFraction(count++ / size);
        indicator.setText2(MavenProjectBundle.message("progress.details.configuring.module", module.getName()));
        for (MavenModuleConfigurer configurer : configurers) {
          configurer.configure(importData.getMavenProject(), myProject, module);
        }
      }
      LOG.info("[maven import] configuring projects took " + (System.currentTimeMillis() - startTime) + "ms");
    });
  }

  private void disposeModifiableModels() {
    MavenUtil.invokeAndWaitWriteAction(myProject, () -> myModelsProvider.dispose());
  }

  private void deleteModules(@NotNull List<Module> modules) {
    for (Module each : modules) {
      if (!each.isDisposed()) {
        myModuleModel.disposeModule(each);
      }
    }
  }


  private void removeOutdatedCompilerConfigSettings() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    final JpsJavaCompilerOptions javacOptions = JavacConfiguration.getOptions(myProject, JavacConfiguration.class);
    String options = javacOptions.ADDITIONAL_OPTIONS_STRING;
    options = options.replaceFirst("(-target (\\S+))", ""); // Old IDEAs saved
    javacOptions.ADDITIONAL_OPTIONS_STRING = options;
  }

  private List<MavenModuleImporter> importModules(MavenModuleImportContext moduleImportContext) {
    List<MavenModuleImportData> allModules = moduleImportContext.allModules;
    List<MavenModuleImporter> importers = new ArrayList<>();

    for (MavenModuleImportData importData : allModules) {
      if (!importData.hasChanges()) {
        importers.add(createModuleImporter(importData, moduleImportContext.moduleNameByProject));
        continue;
      }

      MavenProject mavenProject = importData.getMavenProject();
      MavenId mavenId = mavenProject.getMavenId();
      Module module = importData.getModuleData().getModule();
      myModelsProvider.registerModulePublication(
        module, new ProjectId(mavenId.getGroupId(), mavenId.getArtifactId(), mavenId.getVersion())
      );
      MavenModuleImporter moduleImporter = createModuleImporter(importData, moduleImportContext.moduleNameByProject);
      importers.add(moduleImporter);

      MavenRootModelAdapterLegacyImpl delegate = new MavenRootModelAdapterLegacyImpl(mavenProject, module, myModelsProvider);
      MavenRootModelAdapter rootModelAdapter = new MavenRootModelAdapter(delegate);
      configModule(importData, moduleImporter, rootModelAdapter);
    }

    return importers;
  }

  private static void configModule(@NotNull MavenModuleImportData importData,
                                   @NotNull MavenModuleImporter moduleImporter,
                                   @NotNull MavenRootModelAdapter rootModelAdapter) {
    MavenModuleType type = importData.getModuleData().getType();
    rootModelAdapter.init(importData.getModuleData().isNewModule());
    if (type == AGGREGATOR || type == MAIN_TEST) {
      moduleImporter.config(rootModelAdapter, importData);
    }
    else if (type == AGGREGATOR_MAIN_TEST) {
      moduleImporter.configMainAndTestAggregator(rootModelAdapter, importData);
    }
    else {
      moduleImporter.configMainAndTest(rootModelAdapter, importData);
    }
  }

  private static void configFacets(List<MavenProjectsProcessorTask> tasks, List<MavenModuleImporter> importers) {
    for (MavenModuleImporter importer : importers) {
      if (importer.isAggregatorMainTestModule()) continue;
      importer.preConfigFacets();
    }

    for (MavenModuleImporter importer : importers) {
      if (importer.isAggregatorMainTestModule()) continue;
      importer.configFacets(tasks);
    }

    for (MavenModuleImporter importer : importers) {
      if (importer.isAggregatorMainTestModule()) continue;
      importer.postConfigFacets();
    }
  }

  private void setMavenizedModules(final Collection<Module> modules, final boolean mavenized) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    WorkspaceEntityStorage initialStorage = WorkspaceModel.getInstance(myProject).getEntityStorage().getCurrent();
    WorkspaceEntityStorageBuilder storageBuilder = WorkspaceEntityStorageBuilder.from(initialStorage);
    for (Module module : modules) {
      if (module.isDisposed()) continue;
      ExternalSystemModulePropertyManager modulePropertyManager = ExternalSystemModulePropertyManager.getInstance(module);
      if (modulePropertyManager instanceof ExternalSystemModulePropertyManagerBridge &&
          module instanceof ModuleBridge &&
          ((ModuleBridge)module).getDiff() == null) {
        ((ExternalSystemModulePropertyManagerBridge)modulePropertyManager).setMavenized(mavenized, storageBuilder);
      }
      else {
        modulePropertyManager.setMavenized(mavenized);
      }
    }
    WorkspaceModel.getInstance(myProject).updateProjectModel(builder -> {
      builder.addDiff(storageBuilder);
      return null;
    });
  }

  private MavenModuleImporter createModuleImporter(MavenModuleImportData importData,
                                                   Map<MavenProject, String> moduleNameByProject) {
    return new MavenModuleImporter(importData.getModuleData().getModule(),
                                   myProjectsTree,
                                   importData.getMavenProject(),
                                   importData.getChanges(),
                                   moduleNameByProject,
                                   myImportingSettings,
                                   myModelsProvider,
                                   importData.getModuleData().getType());
  }

  private void removeUnusedProjectLibraries(List<MavenModuleImportData> changedModuleImportData) {
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

  private Collection<ModuleRootModel> collectModuleModels(List<MavenModuleImportData> changedModuleImportData) {
    Map<Module, ModuleRootModel> rootModels = new HashMap<>();
    for (MavenModuleImportData importData : changedModuleImportData) {
      Module module = importData.getModuleData().getModule();
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
  public @NotNull List<Module> getCreatedModules() {
    return myContext.createdModules;
  }
}
