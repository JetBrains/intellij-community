// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

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
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
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
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

import java.io.IOException;
import java.util.*;

import static org.jetbrains.idea.maven.project.MavenProjectChanges.ALL;

class MavenProjectImporterImpl extends MavenProjectImporterBase {
  private static final Logger LOG = Logger.getInstance(MavenProjectImporterImpl.class);
  private final Project myProject;
  private final Map<VirtualFile, Module> myFileToModuleMapping;
  private volatile Set<MavenProject> myAllProjects;
  private final boolean myImportModuleGroupsRequired;

  private final IdeModifiableModelsProvider myIdeModifiableModelsProvider;
  private final WorkspaceEntityStorageBuilder myDiff;
  private ModifiableModelsProviderProxy myModelsProvider;
  private ModuleModelProxy myModuleModel;
  private final Module myDummyModule;

  private final List<Module> myCreatedModules = new ArrayList<>();

  private final Map<MavenProject, Module> myMavenProjectToModule = new HashMap<>();
  private final Map<MavenProject, String> myMavenProjectToModuleName = new HashMap<>();
  private final Map<MavenProject, String> myMavenProjectToModulePath = new HashMap<>();

  MavenProjectImporterImpl(@NotNull Project p,
                           @NotNull MavenProjectsTree projectsTree,
                           @NotNull Map<VirtualFile, Module> fileToModuleMapping,
                           @NotNull Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges,
                           boolean importModuleGroupsRequired,
                           @NotNull IdeModifiableModelsProvider modelsProvider,
                           @NotNull MavenImportingSettings importingSettings,
                           @Nullable Module dummyModule) {
    super(projectsTree, importingSettings, projectsToImportWithChanges);
    myProject = p;
    myFileToModuleMapping = fileToModuleMapping;
    myImportModuleGroupsRequired = importModuleGroupsRequired;
    myDummyModule = dummyModule;

    myDiff = ((IdeModifiableModelsProviderImpl)modelsProvider).getActualStorageBuilder();
    myIdeModifiableModelsProvider = modelsProvider;
  }

  @Override
  @Nullable
  public List<MavenProjectsProcessorTask> importProject() {
    long startTime = System.currentTimeMillis();
    if (MavenUtil.newModelEnabled(myProject)) {
      myModelsProvider = new ModifiableModelsProviderProxyImpl(myProject, myDiff);
    } else {
      myModelsProvider = new ModifiableModelsProviderProxyWrapper(myIdeModifiableModelsProvider);
    }
    myModuleModel = myModelsProvider.getModuleModelProxy();
    List<MavenProjectsProcessorTask> tasks = importProjectOldWay();
    LOG.info("[maven import] applying models took " + (System.currentTimeMillis() - startTime) + "ms");
    return tasks;
  }

  @Nullable
  private List<MavenProjectsProcessorTask> importProjectOldWay() {
    List<MavenProjectsProcessorTask> postTasks = new ArrayList<>();
    boolean hasChanges;

    // in the case projects are changed during importing we must memorise them
    myAllProjects = new LinkedHashSet<>(myProjectsTree.getProjects());

    myAllProjects.addAll(myProjectsToImportWithChanges.keySet()); // some projects may already have been removed from the tree

    hasChanges = deleteIncompatibleModules();
    myProjectsToImportWithChanges = collectProjectsToImport(myProjectsToImportWithChanges);

    mapMavenProjectsToModulesAndNames();

    if (myProject.isDisposed()) return null;

    final boolean projectsHaveChanges = projectsToImportHaveChanges();
    final List<MavenModuleImporter> importers = new ArrayList<>();
    if (projectsHaveChanges) {
      hasChanges = true;
      importers.addAll(importModules());
      scheduleRefreshResolvedArtifacts(postTasks);
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
      MavenUtil.invokeAndWaitWriteAction(myProject, () -> {
        ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
          setMavenizedModules(obsoleteModules, false);
          if (isDeleteObsoleteModules) {
            deleteModules(obsoleteModules);
          }
          removeUnusedProjectLibraries();

          myModelsProvider.commit();

          if (projectsHaveChanges) {
            removeOutdatedCompilerConfigSettings();
          }

          if (projectsHaveChanges) {
            setMavenizedModules(ContainerUtil.map(myProjectsToImportWithChanges.keySet(), myMavenProjectToModule::get), true);
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
        } finally {
          MavenUtil.invokeAndWaitWriteAction(myProject, () -> {
            ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(() -> {
              provider.commit();
            });
          });
        }
      }

      configureMavenProjectsInBackground(myAllProjects, myMavenProjectToModule, myProject);
    }
    else {
      MavenUtil.invokeAndWaitWriteAction(myProject, () -> setMavenizedModules(obsoleteModules, false));
      disposeModifiableModels();
    }

    return postTasks;
  }

  private void disposeModifiableModels() {
    MavenUtil.invokeAndWaitWriteAction(myProject, () -> myModelsProvider.dispose());
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
        result.put(each, ALL);
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
   * @return the first List in returned Pair contains already mavenized modules, the second List - not mavenized
   */
  private Pair<List<Pair<MavenProject, Module>>, List<Pair<MavenProject, Module>>> collectIncompatibleModulesWithProjects() {
    List<Pair<MavenProject, Module>> incompatibleMavenized = new ArrayList<>();
    List<Pair<MavenProject, Module>> incompatibleNotMavenized = new ArrayList<>();

    MavenProjectsManager manager = MavenProjectsManager.getInstance(myProject);
    for (MavenProject each : myAllProjects) {
      Module module = myFileToModuleMapping.get(each.getFile());
      if (module == null) continue;

      if (shouldCreateModuleFor(each) && !(ModuleType.get(module).equals(each.getModuleType()))) {
        (manager.isMavenizedModule(module) ? incompatibleMavenized : incompatibleNotMavenized).add(Pair.create(each, module));
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
      if (manager.isMavenizedModule(each)) {
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

  private void removeOutdatedCompilerConfigSettings() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    final JpsJavaCompilerOptions javacOptions = JavacConfiguration.getOptions(myProject, JavacConfiguration.class);
    String options = javacOptions.ADDITIONAL_OPTIONS_STRING;
    options = options.replaceFirst("(-target (\\S+))", ""); // Old IDEAs saved
    javacOptions.ADDITIONAL_OPTIONS_STRING = options;
  }

  private List<MavenModuleImporter> importModules() {
    Map<MavenProject, MavenProjectChanges> projectsWithChanges = myProjectsToImportWithChanges;

    Set<MavenProject> projectsWithNewlyCreatedModules = new HashSet<>();

    for (MavenProject each : projectsWithChanges.keySet()) {
      if (ensureModuleCreated(each)) {
        projectsWithNewlyCreatedModules.add(each);
      }
    }

    List<MavenModuleImporter> importers = new ArrayList<>();

    for (Map.Entry<MavenProject, MavenProjectChanges> each : projectsWithChanges.entrySet()) {
      MavenProject project = each.getKey();
      Module module = myMavenProjectToModule.get(project);
      boolean isNewModule = projectsWithNewlyCreatedModules.contains(project);
      MavenId mavenId = project.getMavenId();
      myModelsProvider.registerModulePublication(
        module, new ProjectId(mavenId.getGroupId(), mavenId.getArtifactId(), mavenId.getVersion()));
      MavenModuleImporter moduleImporter = createModuleImporter(module, project, each.getValue());
      importers.add(moduleImporter);

      MavenRootModelAdapter rootModelAdapter =
        new MavenRootModelAdapter(new MavenRootModelAdapterLegacyImpl(project, module, myModelsProvider));
      rootModelAdapter.init(isNewModule);
      moduleImporter.config(rootModelAdapter);
    }

    for (MavenProject project : myAllProjects) {
      if (!projectsWithChanges.containsKey(project)) {
        Module module = myMavenProjectToModule.get(project);
        if (module == null) continue;

        importers.add(createModuleImporter(module, project, null));
      }
    }

    return importers;
  }

  private void configFacets(List<MavenProjectsProcessorTask> tasks, List<MavenModuleImporter> importers) {
    for (MavenModuleImporter importer : importers) {
      importer.preConfigFacets();
    }

    for (MavenModuleImporter importer : importers) {
      importer.configFacets(tasks);
    }

    for (MavenModuleImporter importer : importers) {
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
      if (modulePropertyManager instanceof ExternalSystemModulePropertyManagerBridge && module instanceof ModuleBridge && ((ModuleBridge)module).getDiff() == null) {
        ((ExternalSystemModulePropertyManagerBridge)modulePropertyManager).setMavenized(mavenized, storageBuilder);
      } else {
        modulePropertyManager.setMavenized(mavenized);
      }
    }
    WorkspaceModel.getInstance(myProject).updateProjectModel(builder -> {
      builder.addDiff(storageBuilder);
      return null;
    });
  }

  private boolean ensureModuleCreated(MavenProject project) {
    Module existingModule = myMavenProjectToModule.get(project);
    if (existingModule != null && existingModule != myDummyModule) return false;
    final String path = myMavenProjectToModulePath.get(project);
    String moduleName = ModulePathKt.getModuleNameByFilePath(path);
    if (isForTheDummyModule(project, existingModule)) {
      try {
        if (!myDummyModule.getName().equals(moduleName)) {
          myModuleModel.renameModule(myDummyModule, moduleName);
        }
      }
      catch (ModuleWithNameAlreadyExists e) {
        MavenLog.LOG.error("Cannot rename dummy module:", e);
      }
      myMavenProjectToModule.put(project, myDummyModule);
      myCreatedModules.add(myDummyModule);
      return true;
    }


    // for some reason newModule opens the existing iml file, so we
    // have to remove it beforehand.
    deleteExistingImlFile(path);
    deleteExistingModuleByName(moduleName);
    final Module module = myModuleModel.newModule(path, project.getModuleType().getId());

    myMavenProjectToModule.put(project, module);
    myCreatedModules.add(module);
    return true;
  }

  private boolean isForTheDummyModule(MavenProject project, Module existingModule) {
    if (myDummyModule == null) return false;
    if (existingModule == myDummyModule) return true;
    return MavenProjectsManager.getInstance(myProject).getRootProjects().size() == 1 &&
           MavenProjectsManager.getInstance(myProject).findRootProject(project) == project;
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

  private MavenModuleImporter createModuleImporter(Module module, MavenProject mavenProject, @Nullable MavenProjectChanges changes) {
    return new MavenModuleImporter(module,
                                   myProjectsTree,
                                   mavenProject,
                                   changes,
                                   myMavenProjectToModuleName,
                                   myImportingSettings,
                                   myModelsProvider);
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
      if (!isDisposed(each) && MavenRootModelAdapter.isMavenLibrary(each) && !MavenRootModelAdapter.isChangedByUser(each)) {
        myModelsProvider.removeLibrary(each);
        removed = true;
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
  public @NotNull List<Module> getCreatedModules() {
    return myCreatedModules;
  }

}
