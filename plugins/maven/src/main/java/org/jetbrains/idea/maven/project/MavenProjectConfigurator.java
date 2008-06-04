package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.io.IOException;
import java.util.*;

public class MavenProjectConfigurator {
  private Project myProject;
  private ModifiableModuleModel myModuleModel;
  private MavenProjectsTree myMavenTree;
  private Map<VirtualFile, Module> myFileToModuleMapping;
  private MavenImporterSettings myImporterSettings;
  private List<ModifiableRootModel> myRootModelsToCommit = new ArrayList<ModifiableRootModel>();

  private Map<MavenProjectModel, Module> myMavenProjectToModule = new HashMap<MavenProjectModel, Module>();
  private Map<MavenProjectModel, String> myMavenProjectToModuleName = new HashMap<MavenProjectModel, String>();
  private Map<MavenProjectModel, String> myMavenProjectToModulePath = new HashMap<MavenProjectModel, String>();


  public MavenProjectConfigurator(Project p,
                                  MavenProjectsTree projectsTree,
                                  Map<VirtualFile, Module> fileToModuleMapping,
                                  MavenImporterSettings importerSettings) {
    myProject = p;
    myMavenTree = projectsTree;
    myFileToModuleMapping = fileToModuleMapping;
    myImporterSettings = importerSettings;
  }

  public void config() {
    myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    mapModulesToMavenProjects();
    configSettings();
    deleteObsoleteModules();
    configModules();
    configModuleGroups();
    commit();
  }

  private void mapModulesToMavenProjects() {
    for (MavenProjectModel each : myMavenTree.getProjects()) {
      myMavenProjectToModule.put(each, myFileToModuleMapping.get(each.getFile()));
    }
    MavenModuleNameMapper.map(myMavenTree,
                              myMavenProjectToModule,
                              myMavenProjectToModuleName,
                              myMavenProjectToModulePath,
                              myImporterSettings.getDedicatedModuleDir());
  }

  private void configSettings() {
    ((ProjectEx)myProject).setSavePathsRelative(true);
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
                                          ProjectBundle.message("maven.import"),
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

  private void configModules() {
    List<MavenProjectModel> projects = myMavenTree.getProjects();

    for (MavenProjectModel each : projects) {
      ensureModuleCreated(each);
    }

    LinkedHashMap<Module, ModifiableRootModel> rootModels = new LinkedHashMap<Module, ModifiableRootModel>();
    for (MavenProjectModel each : projects) {
      Module module = myMavenProjectToModule.get(each);
      rootModels.put(module, createModuleConfigurator(module, each).config());
    }

    for (MavenProjectModel each : projects) {
      Module module = myMavenProjectToModule.get(each);
      createModuleConfigurator(module, each).preConfigFacets(rootModels.get(module));
    }

    for (MavenProjectModel each : projects) {
      Module module = myMavenProjectToModule.get(each);
      createModuleConfigurator(module, each).configFacets(rootModels.get(module));
    }

    myRootModelsToCommit.addAll(rootModels.values());

    ArrayList<Module> modules = new ArrayList<Module>(myMavenProjectToModule.values());
    MavenProjectsManager.getInstance(myProject).setMavenizedModules(modules);
  }

  private void ensureModuleCreated(MavenProjectModel project) {
    if (myMavenProjectToModule.get(project) != null) return;

    String path = myMavenProjectToModulePath.get(project);
    // for some reason newModule opens the existing iml file, so we
    // have to remove it beforehand.
    removeExistingIml(path);
    myMavenProjectToModule.put(project, myModuleModel.newModule(path, StdModuleTypes.JAVA));
  }

  private MavenModuleConfigurator createModuleConfigurator(Module module, MavenProjectModel mavenProject) {
    return new MavenModuleConfigurator(module,
                                       myModuleModel,
                                       myMavenTree,
                                       mavenProject,
                                       myMavenProjectToModuleName,
                                       myImporterSettings
    );
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
    if (!myImporterSettings.isCreateModuleGroups()) return;

    final Stack<String> groups = new Stack<String>();
    final boolean createTopLevelGroup = myMavenTree.getRootProjects().size() > 1;

    myMavenTree.visit(new MavenProjectsTree.SimpleVisitor() {
      int depth = 0;

      public void visit(MavenProjectModel project) {
        depth++;

        String name = myMavenProjectToModuleName.get(project);

        if (shouldCreateGroup(project)) {
          groups.push(ProjectBundle.message("module.group.name", name));
        }

        Module module = myModuleModel.findModuleByName(name);
        myModuleModel.setModuleGroupPath(module, groups.isEmpty() ? null : groups.toArray(new String[groups.size()]));
      }

      public void leave(MavenProjectModel node) {
        if (shouldCreateGroup(node)) {
          groups.pop();
        }
        depth--;
      }

      private boolean shouldCreateGroup(MavenProjectModel node) {
        return !myMavenTree.getModules(node).isEmpty()
               && (createTopLevelGroup || depth > 1);
      }
    });
  }

  private void commit() {
    ModifiableRootModel[] rootModels = myRootModelsToCommit.toArray(new ModifiableRootModel[myRootModelsToCommit.size()]);
    ProjectRootManager.getInstance(myProject).multiCommit(myModuleModel, rootModels);
  }
}
