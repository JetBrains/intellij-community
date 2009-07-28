package org.jetbrains.idea.maven.importing;

import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.Stack;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenProjectImporter {
  private final Project myProject;
  private final MavenProjectsTree myProjectsTree;
  private final Map<VirtualFile, Module> myFileToModuleMapping;
  private final Set<MavenProject> myProjectsToImport;
  private final MavenModifiableModelsProvider myModelsProvider;
  private final MavenImportingSettings myImportingSettings;

  private final ModifiableModuleModel myModuleModel;

  private final List<Module> myCreatedModules = new ArrayList<Module>();

  private final Map<MavenProject, Module> myMavenProjectToModule = new THashMap<MavenProject, Module>();
  private final Map<MavenProject, String> myMavenProjectToModuleName = new THashMap<MavenProject, String>();
  private final Map<MavenProject, String> myMavenProjectToModulePath = new THashMap<MavenProject, String>();

  public MavenProjectImporter(Project p,
                              MavenProjectsTree projectsTree,
                              Map<VirtualFile, Module> fileToModuleMapping,
                              Set<MavenProject> projectsToImport,
                              final MavenModifiableModelsProvider modelsProvider,
                              MavenImportingSettings importingSettings) {
    myProject = p;
    myProjectsTree = projectsTree;
    myFileToModuleMapping = fileToModuleMapping;
    myModelsProvider = modelsProvider;
    myImportingSettings = importingSettings;

    myModuleModel = modelsProvider.getModuleModel();
    myProjectsToImport = collectProjectsToImport(projectsToImport);
  }

  private Set<MavenProject> collectProjectsToImport(Set<MavenProject> projectsToImport) {
    Set<MavenProject> result = new THashSet<MavenProject>(projectsToImport);
    result.addAll(collectNewlyCreatedProjects());
    return selectProjectsToImport(result);
  }

  private Set<MavenProject> collectNewlyCreatedProjects() {
    Set<MavenProject> result = new THashSet<MavenProject>();

    final Map<MavenProject, Module> projectsWithInconsistentModuleType = new HashMap<MavenProject, Module>();

    for (MavenProject each : myProjectsTree.getProjects()) {
      Module module = myFileToModuleMapping.get(each.getFile());
      if (module == null) {
        result.add(each);
      } else {
        if (shouldCreateModuleFor(each) && !(module.getModuleType() instanceof JavaModuleType)) {
          projectsWithInconsistentModuleType.put(each, module);
        }
      }
    }

    removeModulesOrIgnoreMavenProjects(projectsWithInconsistentModuleType);
    for (final MavenProject mavenProject : projectsWithInconsistentModuleType.keySet()) {
      if (!myProjectsTree.isIgnored(mavenProject)) {
        result.add(mavenProject);
      }
    }

    return result;
  }

  private void removeModulesOrIgnoreMavenProjects(final Map<MavenProject, Module> projectsWithInconsistentModuleType) {
    if (projectsWithInconsistentModuleType.isEmpty()) {
      return;
    }

    final int[] result = new int[1];
    MavenUtil.invokeAndWait(myProject, ModalityState.NON_MODAL, new Runnable() {
      public void run() {
        result[0] = Messages.showDialog(myProject, ProjectBundle.message("maven.import.message.remove.modules",
                                                                         formatModules(projectsWithInconsistentModuleType.values()),
                                                                         ProjectBundle.message("maven.continue.button"),
                                                                         ProjectBundle.message("maven.skip.button"),
                                                                         formatProjects(projectsWithInconsistentModuleType.keySet())),
                                        ProjectBundle.message("maven.tab.importing"),
                                        new String[]{ProjectBundle.message("maven.continue.button"),
                                          ProjectBundle.message("maven.skip.button")}, 0, Messages.getQuestionIcon());
      }
    });
    if (result[0] == 0) {
      for (Map.Entry<MavenProject, Module> entry : projectsWithInconsistentModuleType.entrySet()) {
        myFileToModuleMapping.remove(entry.getKey().getFile());
        final Module module = entry.getValue();
        if (!module.isDisposed()) {
          myModuleModel.disposeModule(module);
        }
      }
    }
    else {
      myProjectsTree.setIgnoredStateDoNotFireEvent(projectsWithInconsistentModuleType.keySet(), true);
    }
  }

  private static String formatModules(final Collection<Module> modules) {
    return StringUtil.join(modules, new Function<Module, String>() {
      public String fun(final Module m) {
        return "'" + m.getName() + "'";
      }
    }, "\n");
  }

  private static String formatProjects(final Collection<MavenProject> projects) {
    return StringUtil.join(projects, new Function<MavenProject, String>() {
      public String fun(final MavenProject project) {
        return "'" + project.getName() + "'";
      }
    }, "\n");
  }

  private Set<MavenProject> selectProjectsToImport(Collection<MavenProject> originalProjects) {
    Set<MavenProject> result = new THashSet<MavenProject>();
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

  public List<MavenProjectsProcessorTask> importProject() {
    final List<MavenProjectsProcessorTask> postTasks = new ArrayList<MavenProjectsProcessorTask>();

    mapModulesToMavenProjects();
    importModules(postTasks);
    configModuleGroups();
    scheduleRefreshResolvedArtifacts(postTasks);
    configSettings();
    deleteObsoleteModules();
    removeUnusedProjectLibraries();
    myModelsProvider.commit();

    return postTasks;
  }

  private void scheduleRefreshResolvedArtifacts(List<MavenProjectsProcessorTask> postTasks) {
    // We have to refresh all the resolved artifacts manually in order to
    // update all the VirtualFilePointers. It is not enough to call
    // VirtualFileManager.refresh() since the newly created files will be only
    // picked by FS when FileWathcer finishes its work. And in the case of import
    // it doesn't finish in time.
    // I couldn't manage to write a test for this since behaviour of VirtualFileManager
    // and FileWatcher differs from real-life execution.

    List<MavenArtifact> artifacts = new ArrayList<MavenArtifact>();
    for (MavenProject each : myProjectsToImport) {
      artifacts.addAll(each.getDependencies());
    }

    final Set<File> files = new THashSet<File>();
    for (MavenArtifact each : artifacts) {
      if (each.isResolved()) files.add(each.getFile());
    }

    final Runnable r = new Runnable() {
      public void run() {
        LocalFileSystem.getInstance().refreshIoFiles(files);
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      r.run();
    }
    else {
      postTasks.add(new MavenProjectsProcessorTask() {
        public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
          throws MavenProcessCanceledException {
          indicator.setText("Refreshing files...");
          r.run();
        }
      });
    }
  }

  private void mapModulesToMavenProjects() {
    List<MavenProject> projects = myProjectsTree.getProjects();
    for (MavenProject each : projects) {
      Module module = myFileToModuleMapping.get(each.getFile());
      if (module != null) {
        myMavenProjectToModule.put(each, module);
      }
    }

    MavenModuleNameMapper.map(projects,
                              myMavenProjectToModule,
                              myMavenProjectToModuleName,
                              myMavenProjectToModulePath,
                              myImportingSettings.getDedicatedModuleDir());
  }

  private void configSettings() {
    ((ProjectEx)myProject).setSavePathsRelative(true);

    String level = calcTargetLevel();
    if (level == null) return;

    String options = JavacSettings.getInstance(myProject).ADDITIONAL_OPTIONS_STRING;
    Pattern pattern = Pattern.compile("(-target (\\S+))");
    Matcher matcher = pattern.matcher(options);

    if (matcher.find()) {
      String currentValue = MavenProject.normalizeCompilerLevel(matcher.group(2));

      if (currentValue == null || compareCompilerLevel(level, currentValue) < 0) {
        StringBuffer buffer = new StringBuffer();
        matcher.appendReplacement(buffer, "-target " + level);
        matcher.appendTail(buffer);
        options = buffer.toString();
      }
    }
    else {
      if (!StringUtil.isEmptyOrSpaces(options)) options += " ";
      options += "-target " + level;
    }
    JavacSettings.getInstance(myProject).ADDITIONAL_OPTIONS_STRING = options;
  }

  private String calcTargetLevel() {
    String maxSource = null;
    String minTarget = null;
    for (MavenProject each : myProjectsTree.getProjects()) {
      String source = each.getSourceLevel();
      String target = each.getTargetLevel();
      if (source != null && (maxSource == null || compareCompilerLevel(maxSource, source) < 0)) maxSource = source;
      if (target != null && (minTarget == null || compareCompilerLevel(minTarget, target) > 0)) minTarget = target;
    }
    return (maxSource != null && compareCompilerLevel(minTarget, maxSource) < 0) ? maxSource : minTarget;
  }

  private int compareCompilerLevel(String left, String right) {
    if (left == null && right == null) return 0;
    if (left == null) return -1;
    if (right == null) return 1;
    return left.compareTo(right);
  }

  private void deleteObsoleteModules() {
    final List<Module> obsolete = collectObsoleteModules();
    if (obsolete.isEmpty()) return;

    MavenProjectsManager.getInstance(myProject).setMavenizedModules(obsolete, false);

    final int[] result = new int[1];
    MavenUtil.invokeAndWait(myProject, ModalityState.NON_MODAL, new Runnable() {
      public void run() {
        result[0] = Messages.showYesNoDialog(myProject,
                                             ProjectBundle.message("maven.import.message.delete.obsolete", formatModules(obsolete)),
                                             ProjectBundle.message("maven.tab.importing"),
                                             Messages.getQuestionIcon());
      }
    });

    if (result[0] == 1) return;// NO

    for (Module each : obsolete) {
      myModuleModel.disposeModule(each);
    }
  }

  private List<Module> collectObsoleteModules() {
    List<Module> remainingModules = new ArrayList<Module>();
    Collections.addAll(remainingModules, myModuleModel.getModules());

    for (MavenProject each : selectProjectsToImport(myProjectsTree.getProjects())) {
      remainingModules.remove(myMavenProjectToModule.get(each));
    }

    List<Module> obsolete = new ArrayList<Module>();
    for (Module each : remainingModules) {
      if (MavenProjectsManager.getInstance(myProject).isMavenizedModule(each)) {
        obsolete.add(each);
      }
    }
    return obsolete;
  }

  private void importModules(final List<MavenProjectsProcessorTask> postTasks) {
    final Set<MavenProject> projects = myProjectsToImport;
    Set<MavenProject> projectsWithNewlyCreatedModules = new THashSet<MavenProject>();

    for (MavenProject each : projects) {
      if (ensureModuleCreated(each)) {
        projectsWithNewlyCreatedModules.add(each);
      }
    }

    final Map<Module, MavenModuleImporter> moduleImporters = new THashMap<Module, MavenModuleImporter>();
    for (MavenProject each : projects) {
      Module module = myMavenProjectToModule.get(each);
      MavenModuleImporter importer = createModuleImporter(module, each);
      moduleImporters.put(module, importer);

      importer.config(projectsWithNewlyCreatedModules.contains(each));
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (MavenProject each : projects) {
          moduleImporters.get(myMavenProjectToModule.get(each)).preConfigFacets();
        }
        for (MavenProject each : projects) {
          moduleImporters.get(myMavenProjectToModule.get(each)).configFacets(postTasks);
        }
      }
    });
    ArrayList<Module> modules = new ArrayList<Module>(moduleImporters.keySet());
    MavenProjectsManager.getInstance(myProject).setMavenizedModules(modules, true);
  }

  private boolean ensureModuleCreated(MavenProject project) {
    if (myMavenProjectToModule.get(project) != null) return false;

    final String path = myMavenProjectToModulePath.get(project);

    // for some reason newModule opens the existing iml file, so we
    // have to remove it beforehand.
    deleteExistingImlFile(path);

    final Module module = myModuleModel.newModule(path, StdModuleTypes.JAVA);
    myMavenProjectToModule.put(project, module);
    myCreatedModules.add(module);
    return true;
  }

  private void deleteExistingImlFile(final String path) {
    new WriteAction() {
      protected void run(Result result) throws Throwable {
        try {
          VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
          if (file != null) file.delete(this);
        }
        catch (IOException ignore) {
        }
      }
    }.execute();
  }

  private MavenModuleImporter createModuleImporter(Module module, MavenProject mavenProject) {
    return new MavenModuleImporter(module,
                                   myProjectsTree,
                                   mavenProject,
                                   myMavenProjectToModuleName,
                                   myImportingSettings,
                                   myModelsProvider);
  }

  private void configModuleGroups() {
    if (!myImportingSettings.isCreateModuleGroups()) return;

    final Stack<String> groups = new Stack<String>();
    final boolean createTopLevelGroup = myProjectsTree.getRootProjects().size() > 1;

    myProjectsTree.visit(new MavenProjectsTree.SimpleVisitor() {
      int depth = 0;

      public void visit(MavenProject each) {
        depth++;

        String name = myMavenProjectToModuleName.get(each);

        if (shouldCreateGroup(each)) {
          groups.push(ProjectBundle.message("module.group.name", name));
        }

        if (!shouldCreateModuleFor(each)) {
          return;
        }

        Module module = myModuleModel.findModuleByName(name);
        if (module == null) {
          // todo: IDEADEV-30669 hook
          String message = "Module " + name + "not found.";
          message += "\nmavenProject=" + each.getFile();
          module = myMavenProjectToModule.get(each);
          message += "\nmyMavenProjectToModule=" + (module == null ? null : module.getName());
          message += "\nmyMavenProjectToModuleName=" + myMavenProjectToModuleName.get(each);
          message += "\nmyMavenProjectToModulePath=" + myMavenProjectToModulePath.get(each);
          MavenLog.LOG.error(message);
          return;
        }

        myModuleModel.setModuleGroupPath(module, groups.isEmpty() ? null : groups.toArray(new String[groups.size()]));
      }

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

  private void removeUnusedProjectLibraries() {
    Set<Library> allLibraries = new THashSet<Library>();
    Collections.addAll(allLibraries, myModelsProvider.getAllLibraries());

    Set<Library> usedLibraries = new THashSet<Library>();
    for (ModuleRootModel eachModel : collectModuleModels()) {
      for (OrderEntry eachEntry : eachModel.getOrderEntries()) {
        if (eachEntry instanceof LibraryOrderEntry) {
          Library lib = ((LibraryOrderEntry)eachEntry).getLibrary();
          if (MavenRootModelAdapter.isMavenLibrary(lib)) usedLibraries.add(lib);
        }
      }
    }

    Set<Library> unusedLibraries = new THashSet<Library>(allLibraries);
    unusedLibraries.removeAll(usedLibraries);

    for (Library each : unusedLibraries) {
      if (!MavenRootModelAdapter.isChangedByUser(each)) {
        myModelsProvider.removeLibrary(each);
      }
    }
  }

  private Collection<ModuleRootModel> collectModuleModels() {
    Map<Module, ModuleRootModel> rootModels = new THashMap<Module, ModuleRootModel>();
    for (MavenProject each : myProjectsToImport) {
      Module module = myMavenProjectToModule.get(each);
      ModifiableRootModel rootModel = myModelsProvider.getRootModel(module);
      rootModels.put(module, rootModel);
    }
    for (Module each : myModuleModel.getModules()) {
      if (rootModels.containsKey(each)) continue;
      rootModels.put(each, myModelsProvider.getRootModel(each));
    }
    return rootModels.values();
  }

  public List<Module> getCreatedModules() {
    return myCreatedModules;
  }
}
