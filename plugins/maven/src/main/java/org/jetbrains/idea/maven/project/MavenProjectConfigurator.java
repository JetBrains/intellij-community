package org.jetbrains.idea.maven.project;

import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenProjectConfigurator {
  private final Project myProject;
  private ModifiableModuleModel myModuleModel;
  private ProjectLibrariesProvider myLibrariesProvider;
  private final MavenProjectsTree myMavenTree;
  private final Map<VirtualFile, Module> myFileToModuleMapping;
  private final MavenImportingSettings myImportingSettings;
  private final List<ModifiableRootModel> myRootModelsToCommit = new ArrayList<ModifiableRootModel>();

  private Map<MavenProject, Module> myMavenProjectToModule = new HashMap<MavenProject, Module>();
  private Map<MavenProject, String> myMavenProjectToModuleName = new HashMap<MavenProject, String>();
  private Map<MavenProject, String> myMavenProjectToModulePath = new HashMap<MavenProject, String>();
  private List<Module> myCreatedModules = new ArrayList<Module>();

  public MavenProjectConfigurator(Project p,
                                  MavenProjectsTree projectsTree,
                                  Map<VirtualFile, Module> fileToModuleMapping,
                                  MavenImportingSettings importingSettings) {
    myProject = p;
    myMavenTree = projectsTree;
    myFileToModuleMapping = fileToModuleMapping;
    myImportingSettings = importingSettings;
  }

  public List<MavenProjectsProcessorPostConfigurationTask> config(MavenModuleModelsProvider moduleModelsProvider) {
    List<MavenProjectsProcessorPostConfigurationTask> postTasks = new ArrayList<MavenProjectsProcessorPostConfigurationTask>();

    myModuleModel = moduleModelsProvider.getModuleModel();
    myLibrariesProvider = new ProjectLibrariesProvider(myProject);

    mapModulesToMavenProjects();
    deleteObsoleteModules();
    configModules(postTasks, moduleModelsProvider);
    configModuleGroups();
    refreshResolvedArtifacts();
    configSettings();
    removeUnusedProjectLibraries();

    moduleModelsProvider.commit(myModuleModel, myRootModelsToCommit.toArray(new ModifiableRootModel[myRootModelsToCommit.size()]));

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
    for (MavenProject each : getMavenProjectsToConfigure()) {
      artifacts.addAll(each.getDependencies());
    }

    List<File> files = new ArrayList<File>();
    for (MavenArtifact each : artifacts) {
      if (each.isResolved()) files.add(each.getFile());
    }

    LocalFileSystem.getInstance().refreshIoFiles(files);
  }

  private void mapModulesToMavenProjects() {
    for (MavenProject each : getMavenProjectsToConfigure()) {
      myMavenProjectToModule.put(each, myFileToModuleMapping.get(each.getFile()));
    }
    MavenModuleNameMapper.map(myMavenTree,
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

      if (compareCompilerLevel(level, currentValue) < 0) {
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
    String resultSource = null;
    String resultTarget = null;
    for (MavenProject each : myMavenTree.getProjects()) {
      String source = each.getSourceLevel();
      String target = each.getTargetLevel();
      if (resultSource == null || compareCompilerLevel(source, resultSource) > 0) resultSource = source;
      if (compareCompilerLevel(target, resultTarget) < 0) resultTarget = target;
    }
    return resultSource != null && compareCompilerLevel(resultSource, resultTarget) > 0 ? resultSource : resultTarget;
  }

  private int compareCompilerLevel(String left, String right) {
    if (left == null && right == null) return 0;
    if (left == null) return 1;
    if (right == null) return -1;
    return left.compareTo(right);
  }

  private void deleteObsoleteModules() {
    List<Module> obsolete = collectObsoleteModules();
    if (obsolete.isEmpty()) return;

    MavenProjectsManager.getInstance(myProject).setRegularModules(obsolete);

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
    remainingModules.removeAll(myMavenProjectToModule.values());

    List<Module> obsolete = new ArrayList<Module>();
    for (Module each : remainingModules) {
      if (MavenProjectsManager.getInstance(myProject).isMavenizedModule(each)) {
        obsolete.add(each);
      }
    }
    return obsolete;
  }

  private void configModules(List<MavenProjectsProcessorPostConfigurationTask> postTasks, final MavenModuleModelsProvider rootModelsProvider) {
    List<MavenProject> projects = getMavenProjectsToConfigure();
    Set<MavenProject> projectsWithNewlyCreatedModules = new HashSet<MavenProject>();

    for (MavenProject each : projects) {
      if (ensureModuleCreated(each)) {
        projectsWithNewlyCreatedModules.add(each);
      }
    }

    LinkedHashMap<Module, MavenModuleConfigurator> configurators = new LinkedHashMap<Module, MavenModuleConfigurator>();
    for (MavenProject each : projects) {
      Module module = myMavenProjectToModule.get(each);
      MavenModuleConfigurator c = createModuleConfigurator(module, each, rootModelsProvider);
      configurators.put(module, c);

      c.config(projectsWithNewlyCreatedModules.contains(each));
    }

    for (MavenProject each : projects) {
      configurators.get(myMavenProjectToModule.get(each)).preConfigFacets();
    }

    for (MavenProject each : projects) {
      configurators.get(myMavenProjectToModule.get(each)).configFacets(postTasks);
    }

    for (MavenModuleConfigurator each : configurators.values()) {
      myRootModelsToCommit.add(each.getRootModel());
    }

    ArrayList<Module> modules = new ArrayList<Module>(myMavenProjectToModule.values());
    MavenProjectsManager.getInstance(myProject).setMavenizedModules(modules);
  }

  private List<MavenProject> getMavenProjectsToConfigure() {
    List<MavenProject> result = new ArrayList<MavenProject>();
    for (MavenProject each : myMavenTree.getProjects()) {
      if (!shouldCreateModuleFor(each)) continue;
      result.add(each);
    }
    return result;
  }

  private boolean shouldCreateModuleFor(MavenProject project) {
    return myImportingSettings.isCreateModulesForAggregators() || !project.isAggregator();
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

  private MavenModuleConfigurator createModuleConfigurator(Module module,
                                                           MavenProject mavenProject,
                                                           MavenModuleModelsProvider rootModelsProvider) {
    return new MavenModuleConfigurator(module,
                                       myModuleModel,
                                       myMavenTree,
                                       mavenProject,
                                       myMavenProjectToModuleName,
                                       myImportingSettings,
                                       rootModelsProvider,
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
    final boolean createTopLevelGroup = myMavenTree.getRootProjects().size() > 1;

    myMavenTree.visit(new MavenProjectsTree.SimpleVisitor() {
      int depth = 0;

      public void visit(MavenProject each) {
        depth++;

        String name = myMavenProjectToModuleName.get(each);

        if (shouldCreateGroup(each)) {
          groups.push(ProjectBundle.message("module.group.name", name));
        }

        if (!shouldCreateModuleFor(each)) return;

        Module module = myModuleModel.findModuleByName(name);
        if (module == null) {
          // todo: IDEADEV-30669 hook
          String message = "Module " + name + "not found.";
          message += "\nmavenProject="+each.getFile();
          module = myMavenProjectToModule.get(each);
          message += "\nmyMavenProjectToModule=" + (module == null ? null : module.getName());
          message += "\nmyMavenProjectToModuleName=" +myMavenProjectToModuleName.get(each);
          message += "\nmyMavenProjectToModulePath=" +myMavenProjectToModulePath.get(each);
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

      private boolean shouldCreateGroup(MavenProject node) {
        return !myMavenTree.getModules(node).isEmpty()
               && (createTopLevelGroup || depth > 1);
      }
    });
  }

  private void removeUnusedProjectLibraries() {
    List<Library> mavenLibraries = new ArrayList<Library>();
    for (Library each : myLibrariesProvider.getAllLibraries()) {
      if (MavenRootModelAdapter.isMavenLibrary(each)) mavenLibraries.add(each);
    }
    mavenLibraries.removeAll(myLibrariesProvider.getUsedLibraries());

    for (Library each : mavenLibraries) {
      if (!MavenRootModelAdapter.isChangedByUser(each)) {
        myLibrariesProvider.removeLibrary(each);
      }
    }
  }

  public List<Module> getCreatedModules() {
    return myCreatedModules;
  }
}
