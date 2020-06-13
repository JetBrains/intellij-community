// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.externalSystem.project.PackagingModifiableModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.impl.artifacts.ArtifactManagerImpl;
import com.intellij.packaging.impl.artifacts.ArtifactModelImpl;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.Stack;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.configurers.MavenModuleConfigurer;
import org.jetbrains.idea.maven.importing.worktree.IdeModifiableModelsProviderBridge;
import org.jetbrains.idea.maven.importing.worktree.MavenExternalSource;
import org.jetbrains.idea.maven.importing.worktree.WorkspaceModuleImporter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

import static org.jetbrains.idea.maven.project.MavenProjectChanges.ALL;

public class MavenProjectImporter {
  private static final Logger LOG = Logger.getInstance(MavenProjectImporter.class);
  private final Project myProject;
  private final MavenProjectsTree myProjectsTree;
  private final Map<VirtualFile, Module> myFileToModuleMapping;
  private volatile Map<MavenProject, MavenProjectChanges> myProjectsToImportWithChanges;
  private volatile Set<MavenProject> myAllProjects;
  private final boolean myImportModuleGroupsRequired;
  private final IdeModifiableModelsProvider myModelsProvider;
  private final MavenImportingSettings myImportingSettings;

  private final ModifiableModuleModel myModuleModel;

  private final List<Module> myCreatedModules = new ArrayList<>();

  private final Map<MavenProject, Module> myMavenProjectToModule = new THashMap<>();
  private final Map<MavenProject, String> myMavenProjectToModuleName = new THashMap<>();
  private final Map<MavenProject, String> myMavenProjectToModulePath = new THashMap<>();

  public MavenProjectImporter(Project p,
                              MavenProjectsTree projectsTree,
                              Map<VirtualFile, Module> fileToModuleMapping,
                              Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges,
                              boolean importModuleGroupsRequired,
                              IdeModifiableModelsProvider modelsProvider,
                              MavenImportingSettings importingSettings) {
    myProject = p;
    myProjectsTree = projectsTree;
    myFileToModuleMapping = fileToModuleMapping;
    myProjectsToImportWithChanges = projectsToImportWithChanges;
    myImportModuleGroupsRequired = importModuleGroupsRequired;
    myModelsProvider = modelsProvider;
    myImportingSettings = importingSettings;

    myModuleModel = modelsProvider.getModifiableModuleModel();
  }

  @Nullable
  public List<MavenProjectsProcessorTask> importProject() {
    if (MavenUtil.newModelEnabled(myProject)) {
      return importProjectAsWorkspaceModel();
    }
    else {
      return importProjectOldWay();
    }
  }

  private <T extends WorkspaceEntity> T findFirst(WorkspaceEntityStorage storage, Class<T> klass, Predicate<T> filter) {
    Iterator<T> iterator = storage.entities(klass).iterator();
    while (iterator.hasNext()) {
      T next = iterator.next();
      if (filter.test(next)) {
        return next;
      }
    }
    return null;
  }

  private List<MavenProjectsProcessorTask> importProjectAsWorkspaceModel() {
    //todo need to rewrite MavenModuleImporter and remove duplicated code in this method
    Map<MavenProject, MavenProjectChanges> projectsToImportWithChanges = myProjectsToImportWithChanges;

    List<MavenProjectsProcessorTask> postTasks = new ArrayList<>();

    // in the case projects are changed during importing we must memorise them
    myAllProjects = new LinkedHashSet<>(myProjectsTree.getProjects());

    myAllProjects.addAll(projectsToImportWithChanges.keySet()); // some projects may already have been removed from the tree


    IdeModifiableModelsProviderBridge legacyBridgeModelsProvider = (IdeModifiableModelsProviderBridge)myModelsProvider;
    WorkspaceEntityStorageBuilder diff = legacyBridgeModelsProvider.getDiff();

    for (MavenProject each : myAllProjects) {
      new WorkspaceModuleImporter(myProject, each, myProjectsTree, diff).importModule();
      myMavenProjectToModuleName.put(each, each.getDisplayName());
    }

    Iterator<WorkspaceEntity> entities = diff.entities(WorkspaceEntity.class).iterator();

    while (entities.hasNext()) {
      WorkspaceEntity next = entities.next();
      diff.changeSource(next, MavenExternalSource.getINSTANCE());
    }

    WriteAction.runAndWait(() -> {
      WorkspaceModel.getInstance(myProject).<Void>updateProjectModel(builder -> {
        builder.replaceBySource(it -> it.equals(MavenExternalSource.getINSTANCE()), diff.toStorage());
        return null;
      });
    });


    WorkspaceEntityStorageBuilder facetDiff =
      WorkspaceEntityStorageBuilder.Companion.from(WorkspaceModel.getInstance(myProject).getEntityStorage().getCurrent());
    IdeModifiableModelsProviderBridge providerForFacets = new IdeModifiableModelsProviderBridge(myProject, facetDiff);

    List<Module> modulesToMavenize = new ArrayList<>();
    List<MavenModuleImporter> importers = new ArrayList<>();
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (MavenProject mavenProject : myAllProjects) {
      Module module = moduleManager.findModuleByName(mavenProject.getDisplayName());
      if (module == null) continue;
      myCreatedModules.add(module);
      MavenModuleImporter importer = new MavenModuleImporter(module,
                                                             myProjectsTree,
                                                             mavenProject,
                                                             ALL,
                                                             myMavenProjectToModuleName,
                                                             myImportingSettings,
                                                             providerForFacets);
      importers.add(importer);

      //need for facets importing
      //importer.setRootModelAdapter(new MavenRootModelAdapter(new MavenRootModelAdapterLegacyImpl(mavenProject, module, providerForFacets)));
    }

    configFacets(postTasks, importers);
    setMavenizedModules(modulesToMavenize, true);
    saveFacets(providerForFacets, moduleManager);
    saveArtifacts(providerForFacets);

    WriteAction.runAndWait(() -> {
      WorkspaceModel.getInstance(myProject).<Void>updateProjectModel(builder -> {
        builder.replaceBySource(it -> it.equals(MavenExternalSource.getINSTANCE()), facetDiff.toStorage());
        return null;
      });
    });

    // legacy importerss

    return postTasks;
  }

  private void saveFacets(IdeModifiableModelsProviderBridge providerForFacets, ModuleManager moduleManager) {
    WriteAction.runAndWait(() -> {
      myAllProjects.stream().map(mavenProject -> moduleManager.findModuleByName(mavenProject.getDisplayName()))
        .filter(Objects::nonNull).forEach(module -> providerForFacets.getModifiableFacetModel(module).commit());
    });
  }

  private void saveArtifacts(IdeModifiableModelsProviderBridge provider) {
    ModifiableArtifactModel artifactModel = provider.getModifiableModel(PackagingModifiableModel.class).getModifiableArtifactModel();
    ArtifactManagerImpl manager = (ArtifactManagerImpl)ArtifactManager.getInstance(myProject);
    WriteAction.runAndWait(() -> {
      manager.commit((ArtifactModelImpl)artifactModel);
    });
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
    if (projectsHaveChanges) {
      hasChanges = true;
      importModules(postTasks);
      scheduleRefreshResolvedArtifacts(postTasks);
    }

    if (projectsHaveChanges || myImportModuleGroupsRequired) {
      hasChanges = true;
      configModuleGroups();
    }

    if (myProject.isDisposed()) return null;

    try {
      boolean modulesDeleted = deleteObsoleteModules();
      hasChanges |= modulesDeleted;
      if (hasChanges) {
        removeUnusedProjectLibraries();
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      disposeModifiableModels();
      LOG.error(e);
      return null;
    }

    if (hasChanges) {
      MavenUtil.invokeAndWaitWriteAction(myProject, () -> {
        myModelsProvider.commit();

        if (projectsHaveChanges) {
          removeOutdatedCompilerConfigSettings();
        }
      });


      List<MavenModuleConfigurer> configurers = MavenModuleConfigurer.getConfigurers();

      MavenUtil.runInBackground(myProject, "Configuring projects", false, indicator -> {
        float count = 0;
        for (MavenProject mavenProject : myAllProjects) {
          Module module = myMavenProjectToModule.get(mavenProject);
          if(module == null) {
            continue;
          }
          indicator.setFraction(count++ / myAllProjects.size());
          indicator.setText2("Configuring module " + module.getName());
          for (MavenModuleConfigurer configurer : configurers) {
            configurer.configure(mavenProject, myProject, module);
          }
        }
      });
    }
    else {
      disposeModifiableModels();
    }

    return postTasks;
  }

  private void disposeModifiableModels() {
    MavenUtil.invokeAndWaitWriteAction(myProject, () -> myModelsProvider.dispose());
  }

  private boolean projectsToImportHaveChanges() {
    for (MavenProjectChanges each : myProjectsToImportWithChanges.values()) {
      if (each.hasChanges()) return true;
    }
    return false;
  }

  private Map<MavenProject, MavenProjectChanges> collectProjectsToImport(Map<MavenProject, MavenProjectChanges> projectsToImport) {
    Map<MavenProject, MavenProjectChanges> result = new THashMap<>(projectsToImport);
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
    Map<MavenProject, MavenProjectChanges> result = new THashMap<>();

    for (MavenProject each : myAllProjects) {
      Module module = myFileToModuleMapping.get(each.getFile());
      if (module == null) {
        result.put(each, ALL);
      }
    }

    return result;
  }

  private Set<MavenProject> selectProjectsToImport(Collection<MavenProject> originalProjects) {
    Set<MavenProject> result = new THashSet<>();
    for (MavenProject each : originalProjects) {
      if (!shouldCreateModuleFor(each)) continue;
      result.add(each);
    }
    return result;
  }

  private boolean shouldCreateModuleFor(MavenProject project) {
    if (myProjectsTree.isIgnored(project)) return false;
    return !project.isAggregator() || myImportingSettings.isCreateModulesForAggregators();
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

    final int[] result = new int[1];
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

  private boolean deleteObsoleteModules() {
    final List<Module> obsoleteModules = collectObsoleteModules();
    if (obsoleteModules.isEmpty()) return false;

    setMavenizedModules(obsoleteModules, false);

    final int[] result = new int[1];
    MavenUtil.invokeAndWait(myProject, myModelsProvider.getModalityStateForQuestionDialogs(),
                            () -> result[0] = Messages.showYesNoDialog(myProject,
                                                                       MavenProjectBundle.message("maven.import.message.delete.obsolete",
                                                                                                  formatModules(obsoleteModules)),
                                                                       MavenProjectBundle.message("maven.project.import.title"),
                                                                       Messages.getQuestionIcon()));

    if (result[0] == Messages.NO) return false;// NO

    for (Module each : obsoleteModules) {
      if (!each.isDisposed()) {
        myModuleModel.disposeModule(each);
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

  private static void doRefreshFiles(Set<File> files) {
    LocalFileSystem.getInstance().refreshIoFiles(files);
  }

  private void scheduleRefreshResolvedArtifacts(List<MavenProjectsProcessorTask> postTasks) {
    // We have to refresh all the resolved artifacts manually in order to
    // update all the VirtualFilePointers. It is not enough to call
    // VirtualFileManager.refresh() since the newly created files will be only
    // picked by FS when FileWatcher finishes its work. And in the case of import
    // it doesn't finish in time.
    // I couldn't manage to write a test for this since behaviour of VirtualFileManager
    // and FileWatcher differs from real-life execution.

    List<MavenArtifact> artifacts = new ArrayList<>();
    for (MavenProject each : myProjectsToImportWithChanges.keySet()) {
      artifacts.addAll(each.getDependencies());
    }

    final Set<File> files = new THashSet<>();
    for (MavenArtifact each : artifacts) {
      if (each.isResolved()) files.add(each.getFile());
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      doRefreshFiles(files);
    }
    else {
      postTasks.add(new MavenProjectsProcessorTask() {
        @Override
        public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
          throws MavenProcessCanceledException {
          indicator.setText("Refreshing files...");
          doRefreshFiles(files);
        }
      });
    }
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

  private void importModules(final List<MavenProjectsProcessorTask> tasks) {
    Map<MavenProject, MavenProjectChanges> projectsWithChanges = myProjectsToImportWithChanges;

    Set<MavenProject> projectsWithNewlyCreatedModules = new THashSet<>();

    for (MavenProject each : projectsWithChanges.keySet()) {
      if (ensureModuleCreated(each)) {
        projectsWithNewlyCreatedModules.add(each);
      }
    }

    List<Module> modulesToMavenize = new ArrayList<>();
    List<MavenModuleImporter> importers = new ArrayList<>();

    for (Map.Entry<MavenProject, MavenProjectChanges> each : projectsWithChanges.entrySet()) {
      MavenProject project = each.getKey();
      Module module = myMavenProjectToModule.get(project);
      boolean isNewModule = projectsWithNewlyCreatedModules.contains(project);
      MavenId mavenId = project.getMavenId();
      myModelsProvider.registerModulePublication(
        module, new ProjectId(mavenId.getGroupId(), mavenId.getArtifactId(), mavenId.getVersion()));
      MavenModuleImporter moduleImporter = createModuleImporter(module, project, each.getValue());
      modulesToMavenize.add(module);
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

    configFacets(tasks, importers);
    setMavenizedModules(modulesToMavenize, true);
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
    MavenUtil
      .invokeAndWaitWriteAction(myProject, () -> MavenProjectsManager.getInstance(myProject).setMavenizedModules(modules, mavenized));
  }

  private boolean ensureModuleCreated(MavenProject project) {
    if (myMavenProjectToModule.get(project) != null) return false;

    final String path = myMavenProjectToModulePath.get(project);

    // for some reason newModule opens the existing iml file, so we
    // have to remove it beforehand.
    deleteExistingImlFile(path);

    final Module module = myModuleModel.newModule(path, project.getModuleType().getId());
    myMavenProjectToModule.put(project, module);
    myCreatedModules.add(module);
    return true;
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
    Map<Module, ModuleRootModel> rootModels = new THashMap<>();
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

  public List<Module> getCreatedModules() {
    return myCreatedModules;
  }
}
