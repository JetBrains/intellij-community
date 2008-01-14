package org.jetbrains.idea.maven.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import org.apache.maven.project.MavenProject;

import java.util.Collection;
import java.util.Stack;


public class MavenToIdeaConfigurator {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.project.MavenToIdeaConfigurator");

  private ModifiableModuleModel myModuleModel;
  private MavenProjectModel myProjectModel;
  private MavenToIdeaMapping myMapping;
  private Collection<String> myProfiles;
  private MavenImporterPreferences myPrefs;

  public static void config(ModifiableModuleModel moduleModel,
                            MavenProjectModel projectModel,
                            Collection<String> profiles,
                            MavenToIdeaMapping mapping,
                            MavenImporterPreferences prefs) {
    MavenToIdeaConfigurator c = new MavenToIdeaConfigurator(moduleModel, projectModel, mapping, profiles, prefs);
    c.config();
  }

  private MavenToIdeaConfigurator(ModifiableModuleModel model,
                                  MavenProjectModel projectModel,
                                  MavenToIdeaMapping mapping,
                                  Collection<String> profiles,
                                  MavenImporterPreferences preferences) {
    myModuleModel = model;
    myProjectModel = projectModel;
    myMapping = mapping;
    myProfiles = profiles;
    myPrefs = preferences;
  }

  private void config() {
    configModules();
    configModuleGroups();
    resolveDependenciesAndCommit();
  }

  private void configModules() {
    myProjectModel.visit(new MavenProjectModel.MavenProjectVisitorRoot() {
      public void visit(MavenProjectModel.Node node) {
        convertNode(node);
        for (MavenProjectModel.Node subnode : node.mavenModulesTopoSorted) {
          convertNode(subnode);
        }
      }
    });
  }

  private void convertNode(MavenProjectModel.Node node) {
    final MavenProject mavenProject = node.getMavenProject();

    Module module = myMapping.getModule(node);
    if (module == null) {
      module = myModuleModel.newModule(myMapping.getModuleFilePath(node));
    }

    new MavenToIdeaModuleConfigurator(myModuleModel, myMapping, myProfiles, myPrefs, module, mavenProject).config();
  }

  private void configModuleGroups() {
    final boolean createModuleGroups = myPrefs.isCreateModuleGroups();
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
