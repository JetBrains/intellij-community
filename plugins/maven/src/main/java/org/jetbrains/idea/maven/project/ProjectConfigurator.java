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
import org.apache.maven.project.MavenProject;
import org.jetbrains.idea.maven.core.util.IdeaAPIHelper;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.io.IOException;
import java.util.*;


public class ProjectConfigurator {
  private Project myProject;
  private ModifiableModuleModel myModuleModel;
  private MavenProjectModel myProjectModel;
  private MavenToIdeaMapping myMapping;
  private Collection<String> myProfiles;
  private MavenImporterSettings mySettings;
  private Collection<ModifiableRootModel> myRootModelsToCommit;

  public static void config(Project p,
                            MavenProjectModel projectModel,
                            Collection<String> profiles,
                            MavenToIdeaMapping mapping,
                            MavenImporterSettings settings) {
    ProjectConfigurator c = new ProjectConfigurator(p, projectModel, mapping, profiles, settings);
    c.config();
  }

  private ProjectConfigurator(Project p,
                              MavenProjectModel projectModel,
                              MavenToIdeaMapping mapping,
                              Collection<String> profiles,
                              MavenImporterSettings settings) {
    myProject = p;
    myProjectModel = projectModel;
    myMapping = mapping;
    myProfiles = profiles;
    mySettings = settings;
  }

  private void config() {
    deleteObsoleteModules();

    myModuleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    configSettings();
    configModules();
    configModuleGroups();
    resolveDependenciesAndCommit();
  }

  private void deleteObsoleteModules() {
    List<Module> obsolete = new ArrayList<Module>();

    for (Module m : myMapping.getObsoleteModules()) {
      if (MavenProjectsManager.getInstance(myProject).isImportedModule(m)) {
        obsolete.add(m);
      }
    }

    if (obsolete.isEmpty()) return;

    MavenProjectsManager.getInstance(myProject).setUserModules(obsolete);

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

    IdeaAPIHelper.deleteModules(myMapping.getObsoleteModules());
  }

  private void configSettings() {
    ((ProjectEx)myProject).setSavePathsRelative(true);
  }

  private void configModules() {
    // we must preserve the natural order.
    final LinkedHashMap<MavenProject, Module> modules = new LinkedHashMap<MavenProject, Module>();

    myProjectModel.visit(new MavenProjectModel.PlainNodeVisitor() {
      public void visit(MavenProjectModel.Node node) {
        MavenProjectHolder p = node.getMavenProject();
        Module m = createModule(node);
        if (!p.isValid()) return;
        modules.put(p.getMavenProject(), m);
      }
    });


    LinkedHashMap<Module, ModifiableRootModel> rootModels = new LinkedHashMap<Module, ModifiableRootModel>();
    for (Map.Entry<MavenProject,Module> each : modules.entrySet()) {
      ModifiableRootModel model = createModuleConfigurator(each.getValue(), each.getKey()).config();
      rootModels.put(each.getValue(), model);
    }


    for (Map.Entry<MavenProject,Module> each : modules.entrySet()) {
      createModuleConfigurator(each.getValue(), each.getKey()).configFacets(rootModels.get(each.getValue()));
    }

    myRootModelsToCommit = rootModels.values();

    MavenProjectsManager.getInstance(myProject).setImportedModules(new ArrayList<Module>(modules.values()));
  }

  private Module createModule(MavenProjectModel.Node node) {
    Module module = myMapping.getModule(node);
    if (module == null) {
      String path = myMapping.getModuleFilePath(node);
      // for some reason newModule opens the existing iml file, so we
      // have to remove it beforehand.
      removeExistingIml(path);
      module = myModuleModel.newModule(path, StdModuleTypes.JAVA);
    }
    return module;
  }

  private ModuleConfigurator createModuleConfigurator(Module module, MavenProject mavenProject) {
    return new ModuleConfigurator(myModuleModel, myMapping, myProfiles, mySettings, module, mavenProject);
  }

  private void removeExistingIml(String path)  {
    VirtualFile existingFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (existingFile == null) return;
    try {
      existingFile.delete(this);
    }
    catch (IOException ignore) {
    }
  }

  private void configModuleGroups() {
    if (!mySettings.isCreateModuleGroups()) return;

    final Stack<String> groups = new Stack<String>();

    final boolean createTopLevelGroup = myProjectModel.getRootProjects().size() > 1;

    myProjectModel.visit(new MavenProjectModel.PlainNodeVisitor() {
      int depth = 0;

      public void visit(MavenProjectModel.Node node) {
        depth++;

        String name = myMapping.getModuleName(node.getProjectId());

        if (shouldCreateGroup(node)) {
          groups.push(ProjectBundle.message("module.group.name", name));
        }

        Module module = myModuleModel.findModuleByName(name);
        myModuleModel.setModuleGroupPath(module, groups.isEmpty() ? null : groups.toArray(new String[groups.size()]));
      }

      public void leave(MavenProjectModel.Node node) {
        if (shouldCreateGroup(node)) {
          groups.pop();
        }
        depth--;
      }

      private boolean shouldCreateGroup(MavenProjectModel.Node node) {
        return !node.mySubProjects.isEmpty() && (createTopLevelGroup || depth > 1);
      }
    });
  }

  private void resolveDependenciesAndCommit() {
    for (Module module : myMapping.getExistingModules()) {
      RootModelAdapter a = new RootModelAdapter(module);
      a.resolveModuleDependencies(myMapping.getLibraryNameToModuleName());
    }

    ModifiableRootModel[] rootModels = myRootModelsToCommit.toArray(new ModifiableRootModel[myRootModelsToCommit.size()]);
    ProjectRootManager.getInstance(myProject).multiCommit(myModuleModel, rootModels);
  }
}
