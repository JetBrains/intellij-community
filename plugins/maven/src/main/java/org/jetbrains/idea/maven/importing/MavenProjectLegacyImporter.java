// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.impl.ModulePathKt;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.statistics.MavenImportCollector;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.IOException;
import java.util.*;

class MavenProjectLegacyImporter extends MavenProjectImporterLegacyBase {
  private static final Logger LOG = Logger.getInstance(MavenProjectLegacyImporter.class);
  private final Map<VirtualFile, Module> myFileToModuleMapping;
  private volatile Set<MavenProject> myAllProjects;
  private final boolean myImportModuleGroupsRequired;

  private Module myPreviewModule;

  private final List<Module> myCreatedModules = new ArrayList<>();

  private final Map<MavenProject, Module> myMavenProjectToModule = new HashMap<>();
  private final Map<MavenProject, String> myMavenProjectToModuleName = new HashMap<>();
  private final Map<MavenProject, String> myMavenProjectToModulePath = new HashMap<>();

  MavenProjectLegacyImporter(@NotNull Project p,
                             @NotNull MavenProjectsTree projectsTree,
                             @NotNull Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges,
                             boolean importModuleGroupsRequired,
                             @NotNull IdeModifiableModelsProvider modelsProvider,
                             @NotNull MavenImportingSettings importingSettings,
                             @Nullable Module previewModule) {
    super(p, projectsTree, importingSettings, projectsToImportWithChanges, modelsProvider);
    myFileToModuleMapping = getFileToModuleMapping(p, previewModule, modelsProvider);
    myImportModuleGroupsRequired = importModuleGroupsRequired;
    myPreviewModule = previewModule;
  }

  @Override
  @Nullable
  public List<MavenProjectsProcessorTask> importProject() {
    MavenLog.LOG.info("Importing Maven project using Legacy API");

    List<MavenProjectsProcessorTask> postTasks = new ArrayList<>();
    boolean hasChanges;

    StructuredIdeActivity activity = MavenImportCollector.LEGACY_IMPORT.started(myProject);

    // in the case projects are changed during importing we must memorise them
    myAllProjects = new LinkedHashSet<>(myProjectsTree.getProjects());

    myAllProjects.addAll(myProjectsToImportWithChanges.keySet()); // some projects may already have been removed from the tree

    hasChanges = deleteIncompatibleModules();
    myProjectsToImportWithChanges = collectProjectsToImport(myProjectsToImportWithChanges);

    mapMavenProjectsToModulesAndNames();

    if (myProject.isDisposed()) return null;

    final boolean projectsHaveChanges = projectsToImportHaveChanges(myProjectsToImportWithChanges.values());
    final List<MavenLegacyModuleImporter.ExtensionImporter> extensionImporters = new ArrayList<>();
    if (projectsHaveChanges) {
      hasChanges = true;
      StructuredIdeActivity createModulesPhase = MavenImportCollector.LEGACY_CREATE_MODULES_PHASE.startedWithParent(myProject, activity);
      extensionImporters.addAll(importModules());
      scheduleRefreshResolvedArtifacts(postTasks, myProjectsToImportWithChanges.keySet());
      createModulesPhase.finished();
    }

    if (projectsHaveChanges || myImportModuleGroupsRequired) {
      hasChanges = true;
      configModuleGroups();
    }

    if (myProject.isDisposed()) return null;

    List<Module> obsoleteModules = collectObsoleteModules();
    boolean isDeleteObsoleteModules = isDeleteObsoleteModules(obsoleteModules);
    hasChanges |= isDeleteObsoleteModules;

    if (hasChanges) {
      StructuredIdeActivity deleteObsoletePhase = MavenImportCollector.LEGACY_DELETE_OBSOLETE_PHASE.startedWithParent(myProject, activity);

      MavenUtil.invokeAndWaitWriteAction(myProject, () -> {
        ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
          setMavenizedModules(obsoleteModules, false);
          List<Module> toDelete = new ArrayList<>();
          if (myPreviewModule != null) {
            toDelete.add(myPreviewModule);
            myPreviewModule = null;
          }
          if (isDeleteObsoleteModules) {
            toDelete.addAll(obsoleteModules);
          }
          deleteModules(toDelete);
          removeUnusedProjectLibraries();

          myModelsProvider.commit();

          if (projectsHaveChanges) {
            removeOutdatedCompilerConfigSettings(myProject);
          }

          if (projectsHaveChanges) {
            setMavenizedModules(ContainerUtil.map(myProjectsToImportWithChanges.keySet(), myMavenProjectToModule::get), true);
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

    activity.finished(() -> List.of(MavenImportCollector.NUMBER_OF_MODULES.with(myMavenProjectToModule.size())));

    return postTasks;
  }

  protected boolean projectsToImportHaveChanges(Collection<MavenProjectChanges> changes) {
    for (MavenProjectChanges each : changes) {
      if (each.hasChanges()) return true;
    }
    return false;
  }


  private Map<MavenProject, MavenProjectChanges> collectProjectsToImport(Map<MavenProject, MavenProjectChanges> projectsToImport) {
    Map<MavenProject, MavenProjectChanges> result = new HashMap<>(projectsToImport);
    result.putAll(collectNewlyCreatedProjects()); // e.g. when 'create modules fro aggregators' setting changes

    Set<MavenProject> allProjectsToImport = result.keySet();
    Set<MavenProject> selectedProjectsToImport = selectProjectsToImport(allProjectsToImport);

    Iterator<MavenProject> it = allProjectsToImport.iterator();
    while (it.hasNext()) {
      if (!selectedProjectsToImport.contains(it.next())) it.remove();
    }

    return result;
  }

  private Map<MavenProject, MavenProjectChanges> collectNewlyCreatedProjects() {
    Map<MavenProject, MavenProjectChanges> result = new HashMap<>();

    for (MavenProject each : myAllProjects) {
      Module module = myFileToModuleMapping.get(each.getFile());
      if (module == null) {
        result.put(each, MavenProjectChanges.ALL);
      }
    }

    return result;
  }

  private boolean deleteIncompatibleModules() {
    final Pair<List<Pair<MavenProject, Module>>, List<Pair<MavenProject, Module>>> incompatible = collectIncompatibleModulesWithProjects();
    final List<Pair<MavenProject, Module>> incompatibleMavenized = incompatible.first;
    final List<Pair<MavenProject, Module>> incompatibleNotMavenized = incompatible.second;

    if (incompatibleMavenized.isEmpty() && incompatibleNotMavenized.isEmpty()) return false;

    boolean changed = false;

    // For already mavenized modules the type may change because maven project plugins were resolved and MavenImporter asked to create a module of a different type.
    // In such cases we must change module type silently.
    for (Pair<MavenProject, Module> each : incompatibleMavenized) {
      myFileToModuleMapping.remove(each.first.getFile());
      myModuleModel.disposeModule(each.second);
      changed = true;
    }

    if (incompatibleNotMavenized.isEmpty()) return changed;

    final int[] result = new int[]{Messages.OK};
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      MavenUtil.invokeAndWait(myProject, myModelsProvider.getModalityStateForQuestionDialogs(), () -> {
        String message = MavenProjectBundle.message("maven.import.incompatible.modules",
                                                    incompatibleNotMavenized.size(),
                                                    formatProjectsWithModules(incompatibleNotMavenized));
        String[] options = {
          MavenProjectBundle.message("maven.import.incompatible.modules.recreate"),
          MavenProjectBundle.message("maven.import.incompatible.modules.ignore")
        };

        result[0] = Messages.showOkCancelDialog(myProject, message,
                                                MavenProjectBundle.message("maven.project.import.title"),
                                                options[0], options[1], Messages.getQuestionIcon());
      });
    }

    if (result[0] == Messages.OK) {
      for (Pair<MavenProject, Module> each : incompatibleNotMavenized) {
        myFileToModuleMapping.remove(each.first.getFile());
        myModuleModel.disposeModule(each.second);
      }
      changed = true;
    }
    else {
      myProjectsTree.setIgnoredState(MavenUtil.collectFirsts(incompatibleNotMavenized), true, true);
    }

    return changed;
  }

  /**
   * Collects modules that need to change module type
   *
   * @return the first List in returned Pair contains already mavenized modules, the second List - not mavenized
   */
  private Pair<List<Pair<MavenProject, Module>>, List<Pair<MavenProject, Module>>> collectIncompatibleModulesWithProjects() {
    List<Pair<MavenProject, Module>> incompatibleMavenized = new ArrayList<>();
    List<Pair<MavenProject, Module>> incompatibleNotMavenized = new ArrayList<>();

    for (MavenProject each : myAllProjects) {
      Module module = myFileToModuleMapping.get(each.getFile());
      if (module == null) continue;

      if (shouldCreateModuleFor(each) && !(ModuleType.get(module).equals(each.getModuleType()))) {
        (MavenUtil.isMavenizedModule(module) ? incompatibleMavenized : incompatibleNotMavenized).add(Pair.create(each, module));
      }
    }
    return Pair.create(incompatibleMavenized, incompatibleNotMavenized);
  }

  private static String formatProjectsWithModules(List<Pair<MavenProject, Module>> projectsWithModules) {
    return StringUtil.join(projectsWithModules, each -> {
      MavenProject project = each.first;
      Module module = each.second;
      return ModuleType.get(module).getName() +
             " '" +
             module.getName() +
             "' for Maven project " +
             project.getMavenId().getDisplayString();
    }, "<br>");
  }

  private void deleteModules(@NotNull List<Module> modules) {
    for (Module each : modules) {
      if (!each.isDisposed()) {
        myModuleModel.disposeModule(each);
      }
    }
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

  private List<Module> collectObsoleteModules() {
    List<Module> remainingModules = new ArrayList<>();
    Collections.addAll(remainingModules, myModuleModel.getModules());

    for (MavenProject each : selectProjectsToImport(myAllProjects)) {
      remainingModules.remove(myMavenProjectToModule.get(each));
    }

    List<Module> obsolete = new ArrayList<>();
    final MavenProjectsManager manager = MavenProjectsManager.getInstance(myProject);
    for (Module each : remainingModules) {
      if (manager.isMavenizedModule(each) && myPreviewModule != each) {
        obsolete.add(each);
      }
    }
    return obsolete;
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

  private void mapMavenProjectsToModulesAndNames() {
    for (MavenProject each : myAllProjects) {
      Module module = myFileToModuleMapping.get(each.getFile());
      if (module != null) {
        myMavenProjectToModule.put(each, module);
      }
    }

    MavenModuleNameMapper.map(myAllProjects,
                              myMavenProjectToModule,
                              myMavenProjectToModuleName,
                              myMavenProjectToModulePath,
                              myImportingSettings.getDedicatedModuleDir());
  }

  private List<MavenLegacyModuleImporter.ExtensionImporter> importModules() {
    Map<MavenProject, MavenProjectChanges> projectsWithChanges = myProjectsToImportWithChanges;

    Set<MavenProject> projectsWithNewlyCreatedModules = new HashSet<>();

    if (projectsWithChanges.size() > 0) {
      if (null != myPreviewModule) {
        deleteModules(List.of(myPreviewModule));
        myPreviewModule = null;
      }
    }

    for (MavenProject each : projectsWithChanges.keySet()) {
      if (ensureModuleCreated(each)) {
        projectsWithNewlyCreatedModules.add(each);
      }
    }

    List<MavenLegacyModuleImporter.ExtensionImporter> extensionImporters = new ArrayList<>();

    for (Map.Entry<MavenProject, MavenProjectChanges> each : projectsWithChanges.entrySet()) {
      MavenProject project = each.getKey();
      Module module = myMavenProjectToModule.get(project);
      boolean isNewModule = projectsWithNewlyCreatedModules.contains(project);
      MavenId mavenId = project.getMavenId();
      myModelsProvider.registerModulePublication(
        module, new ProjectId(mavenId.getGroupId(), mavenId.getArtifactId(), mavenId.getVersion()));
      MavenLegacyModuleImporter moduleImporter = createModuleImporter(module, project, each.getValue());
      ContainerUtil.addIfNotNull(extensionImporters, createExtensionImporterIfApplicable(project, module, each.getValue()));

      MavenRootModelAdapter rootModelAdapter =
        new MavenRootModelAdapter(new MavenRootModelAdapterLegacyImpl(project, module, myModelsProvider));
      rootModelAdapter.init(isNewModule);
      moduleImporter.config(rootModelAdapter);
    }

    for (MavenProject project : myAllProjects) {
      if (!projectsWithChanges.containsKey(project)) {
        Module module = myMavenProjectToModule.get(project);
        if (module == null) continue;

        ContainerUtil.addIfNotNull(extensionImporters, createExtensionImporterIfApplicable(project, module, MavenProjectChanges.NONE));
      }
    }

    return extensionImporters;
  }

  private boolean ensureModuleCreated(MavenProject project) {
    Module existingModule = myMavenProjectToModule.get(project);
    if (existingModule != null && existingModule != myPreviewModule) return false;
    final String path = myMavenProjectToModulePath.get(project);
    String moduleName = ModulePathKt.getModuleNameByFilePath(path);

    // for some reason newModule opens the existing iml file, so we
    // have to remove it beforehand.
    deleteExistingImlFile(path);
    deleteExistingModuleByName(moduleName);
    final Module module = myModuleModel.newModule(path, project.getModuleType().getId());

    myMavenProjectToModule.put(project, module);
    myCreatedModules.add(module);
    return true;
  }

  private void deleteExistingModuleByName(final String name) {
    Module module = myModuleModel.findModuleByName(name);
    if (module != null) {
      myModuleModel.disposeModule(module);
    }
  }

  private void deleteExistingImlFile(final String path) {
    MavenUtil.invokeAndWaitWriteAction(myProject, new Runnable() {
      @Override
      public void run() {
        try {
          VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
          if (file != null) file.delete(this);
        }
        catch (IOException e) {
          MavenLog.LOG.warn("Cannot delete existing iml file: " + path, e);
        }
      }
    });
  }

  private MavenLegacyModuleImporter createModuleImporter(@NotNull Module module,
                                                         @NotNull MavenProject mavenProject,
                                                         @NotNull MavenProjectChanges changes) {
    return new MavenLegacyModuleImporter(module, myProjectsTree, mavenProject, myMavenProjectToModuleName, myImportingSettings,
                                         myModelsProvider);
  }


  @Nullable
  private MavenLegacyModuleImporter.ExtensionImporter createExtensionImporterIfApplicable(@NotNull MavenProject mavenProject,
                                                                                          @NotNull Module module,
                                                                                          @NotNull MavenProjectChanges changes) {
    return MavenLegacyModuleImporter.ExtensionImporter.createIfApplicable(
      mavenProject,
      module,
      mavenProject.isAggregator() ? StandardMavenModuleType.AGGREGATOR : StandardMavenModuleType.SINGLE_MODULE,
      myProjectsTree,
      changes,
      myMavenProjectToModuleName,
      false);
  }

  private void configModuleGroups() {
    if (!myImportingSettings.isCreateModuleGroups()) return;

    final Stack<String> groups = new Stack<>();
    final boolean createTopLevelGroup = myProjectsTree.getRootProjects().size() > 1;

    myProjectsTree.visit(new MavenProjectsTree.SimpleVisitor() {
      int depth = 0;

      @Override
      public boolean shouldVisit(MavenProject project) {
        // in case some project has been added while we were importing
        return myMavenProjectToModuleName.containsKey(project);
      }

      @Override
      public void visit(MavenProject each) {
        depth++;

        String name = myMavenProjectToModuleName.get(each);

        if (shouldCreateGroup(each)) {
          groups.push(MavenProjectBundle.message("module.group.name", name));
        }

        if (!shouldCreateModuleFor(each)) {
          return;
        }

        Module module = myModuleModel.findModuleByName(name);
        if (module == null) return;
        myModuleModel.setModuleGroupPath(module, groups.isEmpty() ? null : ArrayUtilRt.toStringArray(groups));
      }

      @Override
      public void leave(MavenProject each) {
        if (shouldCreateGroup(each)) {
          groups.pop();
        }
        depth--;
      }

      private boolean shouldCreateGroup(MavenProject project) {
        return !myProjectsTree.getModules(project).isEmpty()
               && (createTopLevelGroup || depth > 1);
      }
    });
  }

  private boolean removeUnusedProjectLibraries() {
    Set<Library> unusedLibraries = new HashSet<>();
    Collections.addAll(unusedLibraries, myModelsProvider.getAllLibraries());

    for (ModuleRootModel eachModel : collectModuleModels()) {
      for (OrderEntry eachEntry : eachModel.getOrderEntries()) {
        if (eachEntry instanceof LibraryOrderEntry) {
          unusedLibraries.remove(((LibraryOrderEntry)eachEntry).getLibrary());
        }
      }
    }

    boolean removed = false;
    for (Library each : unusedLibraries) {
      if (!isDisposed(each) && MavenRootModelAdapter.isMavenLibrary(each)) {
        if (!MavenRootModelAdapter.isChangedByUser(each)) {
          myModelsProvider.removeLibrary(each);
          removed = true;
        }
        else {
          MavenImportCollector.HAS_USER_MODIFIED_IMPORTED_LIBRARY.log(myProject);
        }
      }
    }
    return removed;
  }

  private static boolean isDisposed(Library library) {
    return library instanceof LibraryEx && ((LibraryEx)library).isDisposed();
  }

  private Collection<ModuleRootModel> collectModuleModels() {
    Map<Module, ModuleRootModel> rootModels = new HashMap<>();
    for (MavenProject each : myProjectsToImportWithChanges.keySet()) {
      Module module = myMavenProjectToModule.get(each);
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
    return myCreatedModules;
  }

  private static Map<VirtualFile, Module> getFileToModuleMapping(
    Project project,
    Module myPreviewModule,
    IdeModifiableModelsProvider modelsProvider) {
    return MavenProjectsManager.getInstance(project)
      .getFileToModuleMapping(new MavenModelsProvider() {
        @Override
        public Module[] getModules() {
          return ArrayUtil.remove(modelsProvider.getModules(), myPreviewModule);
        }

        @Override
        public VirtualFile[] getContentRoots(Module module) {
          return modelsProvider.getContentRoots(module);
        }
      });
  }
}
