package org.jetbrains.idea.maven.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.apache.maven.project.MavenProject;
import org.jetbrains.idea.maven.core.util.IdeaAPIHelper;

import java.util.Collection;
import java.util.Stack;


public class ProjectConfigurator {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.project.MavenToIdeaConfigurator");

  private Project myProject;
  private ModifiableModuleModel myModuleModel;
  private MavenProjectModel myProjectModel;
  private MavenToIdeaMapping myMapping;
  private Collection<String> myProfiles;
  private MavenImporterSettings mySettings;

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

    configModules();
    configModuleGroups();
    resolveDependenciesAndCommit();
  }

  private void deleteObsoleteModules() {
    Collection<Module> modules = myMapping.getObsoleteModules();
    if (modules.isEmpty()) return;

    String formatted = StringUtil.join(modules, new Function<Module, String>() {
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

  private void configModules() {
    myProjectModel.visit(new MavenProjectModel.MavenProjectVisitorRoot() {
      public void visit(MavenProjectModel.Node node) {
        convertNode(node);
        for (MavenProjectModel.Node child : node.mavenModulesTopoSorted) {
          convertNode(child);
        }
      }
    });
  }

  private void convertNode(MavenProjectModel.Node node) {
    final MavenProject mavenProject = node.getMavenProject();

    Module module = myMapping.getModule(node);
    if (module == null) {
      module = myModuleModel.newModule(myMapping.getModuleFilePath(node), StdModuleTypes.JAVA);
    }

    new ModuleConfigurator(myModuleModel, myMapping, myProfiles, mySettings, module, mavenProject).config();
  }

  private void configModuleGroups() {
    final boolean createModuleGroups = mySettings.isCreateModuleGroups();
    final Stack<String> groups = new Stack<String>();

    myProjectModel.visit(new MavenProjectModel.MavenProjectVisitorPlain() {
      public void visit(MavenProjectModel.Node node) {
        String name = myMapping.getModuleName(node.getId());
        LOG.assertTrue(name != null);

        if (createModuleGroups && !node.mavenModules.isEmpty()) {
          groups.push(ProjectBundle.message("module.group.name", name));
        }

        Module module = myModuleModel.findModuleByName(name);
        if (module != null) {
          myModuleModel.setModuleGroupPath(module, groups.isEmpty() ? null : groups.toArray(new String[groups.size()]));
        }
        else {
          LOG.info("Cannot find module " + name);
        }
      }

      public void leave(MavenProjectModel.Node node) {
        if (createModuleGroups && !node.mavenModules.isEmpty()) {
          groups.pop();
        }
      }
    });
  }

  private void resolveDependenciesAndCommit() {
    for (Module module : myMapping.getExistingModules()) {
      RootModelAdapter a = new RootModelAdapter(module);
      a.resolveModuleDependencies(myMapping.getLibraryNameToModuleName());
    }
    myModuleModel.commit();
  }
}
