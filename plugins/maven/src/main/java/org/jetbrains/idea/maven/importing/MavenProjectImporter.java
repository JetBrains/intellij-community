package org.jetbrains.idea.maven.importing;

import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
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
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenLog;

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
  private final MavenModuleModelsProvider myModuleModelsProvider;
  private final MavenImportingSettings myImportingSettings;

  private final ModifiableModuleModel myModuleModel;
  private final MavenProjectLibrariesProvider myLibrariesProvider;

  private final List<Module> myCreatedModules = new ArrayList<Module>();
  private final List<ModifiableRootModel> myRootModelsToCommit = new ArrayList<ModifiableRootModel>();

  private final Map<MavenProject, Module> myMavenProjectToModule = new THashMap<MavenProject, Module>();
  private final Map<MavenProject, String> myMavenProjectToModuleName = new THashMap<MavenProject, String>();
  private final Map<MavenProject, String> myMavenProjectToModulePath = new THashMap<MavenProject, String>();

  public MavenProjectImporter(Project p,
                              MavenProjectsTree projectsTree,
                              Map<VirtualFile, Module> fileToModuleMapping,
                              Set<MavenProject> projectsToImport,
                              MavenModuleModelsProvider moduleModelsProvider,
                              MavenProjectLibrariesProvider librariesProvider,
                              MavenImportingSettings importingSettings) {
    myProject = p;
    myProjectsTree = projectsTree;
    myFileToModuleMapping = fileToModuleMapping;
    myModuleModelsProvider = moduleModelsProvider;
    myImportingSettings = importingSettings;

    myProjectsToImport = collectProjectsToImport(projectsToImport);

    myModuleModel = moduleModelsProvider.getModuleModel();
    myLibrariesProvider = librariesProvider;
  }

  private Set<MavenProject> collectProjectsToImport(Set<MavenProject> projectsToImport) {
    Set<MavenProject> result = new THashSet<MavenProject>(projectsToImport);
    result.addAll(collectNewlyCreatedProjects());
    return selectProjectsToImport(result);
  }

  private Set<MavenProject> collectNewlyCreatedProjects() {
    Set<MavenProject> result = new THashSet<MavenProject>();
    for (MavenProject each : myProjectsTree.getProjects()) {
      Module module = myFileToModuleMapping.get(each.getFile());
      if (module == null) {
        result.add(each);
      }
    }
    return result;
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

  public List<MavenProjectsProcessorPostConfigurationTask> importProject() {
    List<MavenProjectsProcessorPostConfigurationTask> postTasks = new ArrayList<MavenProjectsProcessorPostConfigurationTask>();

    mapModulesToMavenProjects();
    importModules(postTasks);
    configModuleGroups();
    refreshResolvedArtifacts();
    configSettings();
    deleteObsoleteModules();
    removeUnusedProjectLibraries();

    myModuleModelsProvider.commit(myModuleModel, myRootModelsToCommit.toArray(new ModifiableRootModel[myRootModelsToCommit.size()]));

    return postTasks;
  }

  private void refreshResolvedArtifacts() {
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

    List<File> files = new ArrayList<File>();
    for (MavenArtifact each : artifacts) {
      if (each.isResolved()) files.add(each.getFile());
    }

    LocalFileSystem.getInstance().refreshIoFiles(files);
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
    List<Module> obsolete = collectObsoleteModules();
    if (obsolete.isEmpty()) return;

    MavenProjectsManager.getInstance(myProject).setMavenizedModules(obsolete, false);

    String formatted = StringUtil.join(obsolete, new Function<Module, String>() {
      public String fun(Module m) {
        return "'" + m.getName() + "'";
      }
    }, "\n");

    int result = Messages.showYesNoDialog(myProject,
                                          ProjectBundle.message("maven.import.message.delete.obsolete", formatted),
                                          ProjectBundle.message("maven.tab.importing"),
                                          Messages.getQuestionIcon());
    if (result == 1) return;// NO

    for (Module each : obsolete) {
      myModuleModel.disposeModule(each);
    }
  }

  private List<Module> collectObsoleteModules() {
    List<Module> remainingModules = new ArrayList<Module>();
    Collections.addAll(remainingModules, ModuleManager.getInstance(myProject).getModules());

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

  private void importModules(List<MavenProjectsProcessorPostConfigurationTask> postTasks) {
    Set<MavenProject> projects = myProjectsToImport;
    Set<MavenProject> projectsWithNewlyCreatedModules = new THashSet<MavenProject>();

    for (MavenProject each : projects) {
      if (ensureModuleCreated(each)) {
        projectsWithNewlyCreatedModules.add(each);
      }
    }

    Map<Module, MavenModuleImporter> moduleImporters = new THashMap<Module, MavenModuleImporter>();
    for (MavenProject each : projects) {
      Module module = myMavenProjectToModule.get(each);
      MavenModuleImporter importer = createModuleImporter(module, each);
      moduleImporters.put(module, importer);

      importer.config(projectsWithNewlyCreatedModules.contains(each));
    }

    for (MavenProject each : projects) {
      moduleImporters.get(myMavenProjectToModule.get(each)).preConfigFacets();
    }

    for (MavenProject each : projects) {
      moduleImporters.get(myMavenProjectToModule.get(each)).configFacets(postTasks);
    }

    for (MavenModuleImporter each : moduleImporters.values()) {
      myRootModelsToCommit.add(each.getRootModel());
    }

    ArrayList<Module> modules = new ArrayList<Module>(moduleImporters.keySet());
    MavenProjectsManager.getInstance(myProject).setMavenizedModules(modules, true);
  }

  private boolean ensureModuleCreated(MavenProject project) {
    if (myMavenProjectToModule.get(project) != null) return false;

    String path = myMavenProjectToModulePath.get(project);
    // for some reason newModule opens the existing iml file, so we
    // have to remove it beforehand.
    removeExistingIml(path);
    final Module module = myModuleModel.newModule(path, StdModuleTypes.JAVA);
    myMavenProjectToModule.put(project, module);
    myCreatedModules.add(module);
    return true;
  }

  private MavenModuleImporter createModuleImporter(Module module, MavenProject mavenProject) {
    return new MavenModuleImporter(module,
                                   myModuleModel,
                                   myProjectsTree,
                                   mavenProject,
                                   myMavenProjectToModuleName,
                                   myImportingSettings,
                                   myModuleModelsProvider,
                                   myLibrariesProvider);
  }

  private void removeExistingIml(String path) {
    VirtualFile existingFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (existingFile == null) return;
    try {
      existingFile.delete(this);
    }
    catch (IOException ignore) {
    }
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
          MavenLog.LOG.warn(message);
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
    Collections.addAll(allLibraries, myLibrariesProvider.getAllLibraries());

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
        myLibrariesProvider.removeLibrary(each);
      }
    }
  }

  private Collection<ModuleRootModel> collectModuleModels() {
    Map<Module, ModuleRootModel> rootModels = new THashMap<Module, ModuleRootModel>();
    for (ModifiableRootModel each : myRootModelsToCommit) {
      rootModels.put(each.getModule(), each);
    }
    for (Module each : myModuleModel.getModules()) {
      if (rootModels.containsKey(each)) continue;
      rootModels.put(each, myModuleModelsProvider.getRootModel(each));
    }
    return rootModels.values();
  }

  public List<Module> getCreatedModules() {
    return myCreatedModules;
  }
}
