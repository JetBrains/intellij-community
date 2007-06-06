package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.core.util.MavenId;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenToIdeaMappingImpl implements MavenToIdeaMapping {

  final private Map<MavenId, String> projectIdToModuleName = new HashMap<MavenId, String>();

  final private Map<String, Set<MavenProjectModel.Node>> nameToProject = new HashMap<String, Set<MavenProjectModel.Node>>();

  final private Map<MavenProjectModel.Node, String> projectToModuleName = new HashMap<MavenProjectModel.Node, String>();

  final private Map<MavenProjectModel.Node, String> projectToModulePath = new HashMap<MavenProjectModel.Node, String>();

  final private Set<String> duplicateImportedNames = new HashSet<String>();

  final private Map<String, Module> nameToModule = new HashMap<String, Module>();

  final private Map<String, String> libraryNameToModuleName = new HashMap<String, String>();

  final private Map<MavenProjectModel.Node, Module> projectToModule = new HashMap<MavenProjectModel.Node, Module>();

  //final private Map<MavenProjectModel.Node, Module> projectToModuleConflict =
  //  new HashMap<MavenProjectModel.Node, Module>();

  final private Set<Module> obsoleteModules = new HashSet<Module>();

  @NonNls private static final String IML_EXT = ".iml";

  public MavenToIdeaMappingImpl(MavenProjectModel mavenProjectModel, String moduleDir, Project project, boolean obsoleteSyntheticModules) {
    resolveModuleNames(mavenProjectModel);

    resolveModulePaths(mavenProjectModel, moduleDir);

    if (project != null) {
      mapToExistingModules(mavenProjectModel, project, obsoleteSyntheticModules);
    }
  }

  private void resolveModuleNames(final MavenProjectModel mavenProjectModel) {
    mavenProjectModel.visit(new MavenProjectModel.MavenProjectVisitorPlain() {
      public void visit(MavenProjectModel.Node node) {
        final MavenId projectId = node.getId();
        final String name = node.getLinkedModule() != null ? node.getLinkedModule().getName() : generateModuleName(projectId);

        projectIdToModuleName.put(projectId, name);

        projectToModuleName.put(node, name);

        libraryNameToModuleName.put(getLibraryName(projectId), name);

        Set<MavenProjectModel.Node> projects = nameToProject.get(name);
        if (projects == null) {
          projects = new HashSet<MavenProjectModel.Node>();
          nameToProject.put(name, projects);
        }
        else {
          duplicateImportedNames.add(name);
        }
        projects.add(node);
      }
    });
  }

  private void resolveModulePaths(final MavenProjectModel mavenProjectModel, final String dedicatedModuleDir) {
    mavenProjectModel.visit(new MavenProjectModel.MavenProjectVisitorPlain() {
      public void visit(final MavenProjectModel.Node node) {
        final Module module = node.getLinkedModule();
        projectToModulePath.put(node, module != null ? module.getModuleFilePath() : generateModulePath(node, dedicatedModuleDir));
      }
    });
  }

  private void mapToExistingModules(final MavenProjectModel mavenProjectModel,
                                    final Project project,
                                    final boolean obsoleteSyntheticModules) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      nameToModule.put(module.getName(), module);
      if (obsoleteSyntheticModules && SyntheticModuleUtil.isSynthetic(module)) {
        obsoleteModules.add(module);
      }
    }

    mavenProjectModel.visit(new MavenProjectModel.MavenProjectVisitorPlain() {
      public void visit(MavenProjectModel.Node node) {
        Module module = node.getLinkedModule() != null ? node.getLinkedModule() : nameToModule.get(projectToModuleName.get(node));
        if (module != null) {
          if (FileUtil.toSystemIndependentName(module.getModuleFilePath())
            .equalsIgnoreCase(FileUtil.toSystemIndependentName(getModuleFilePath(node)))) {
            projectToModule.put(node, module);
            obsoleteModules.remove(module);
          }
          else {
//            projectToModuleConflict.put(node, module);
            obsoleteModules.add(module);
            node.unlinkModule();
          }
        }
      }
    });

    for (Module obsoleteModule : obsoleteModules) {
      nameToModule.remove(obsoleteModule.getName());
    }
  }

  private String generateModuleName(final MavenId id) {
    return id.artifactId;
  }

  private String generateModulePath(final MavenProjectModel.Node node, final String dedicatedModuleDir) {
    return new File(StringUtil.isEmptyOrSpaces(dedicatedModuleDir) ? node.getDirectory() : dedicatedModuleDir,
                    projectToModuleName.get(node) + IML_EXT).getPath();
  }

  public String getLibraryName(MavenId id) {
    return id.toString();
  }

  public String getModuleName(MavenId id) {
    final String moduleName = projectIdToModuleName.get(id);
    return nameToModule.get(moduleName) != null || nameToProject.get(moduleName) != null ? moduleName : null;
  }

  public Collection<Module> getExistingModules() {
    return nameToModule.values();
  }

  public Module getModule(final MavenProjectModel.Node node) {
    return projectToModule.get(node);
  }

  public String getModuleFilePath(MavenProjectModel.Node node) {
    return projectToModulePath.get(node);
  }

  public Collection<String> getDuplicateNames() {
    return duplicateImportedNames;
  }

  public Collection<Module> getObsoleteModules() {
    return obsoleteModules;
  }

  public Map<String, String> getLibraryNameToModuleName() {
    return libraryNameToModuleName;
  }
}