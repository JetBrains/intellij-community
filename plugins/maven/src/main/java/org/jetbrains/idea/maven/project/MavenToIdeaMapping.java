package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectId;

import java.io.File;
import java.util.*;

public class MavenToIdeaMapping {
  @NonNls private static final String IML_EXT = ".iml";

  private Map<ProjectId, VirtualFile> projectIdToFile = new HashMap<ProjectId, VirtualFile>();
  private Map<ProjectId, String> projectIdToModuleName = new HashMap<ProjectId, String>();

  private Set<String> projectNames = new HashSet<String>();
  private Map<MavenProjectModel.Node, String> projectNodeToModuleName = new HashMap<MavenProjectModel.Node, String>();
  private Map<MavenProjectModel.Node, String> projectToModulePath = new HashMap<MavenProjectModel.Node, String>();
  private Map<String, Module> nameToModule = new HashMap<String, Module>();
  private Map<String, String> libraryNameToModuleName = new HashMap<String, String>();
  private Map<MavenProjectModel.Node, Module> projectToModule = new HashMap<MavenProjectModel.Node, Module>();
  private Set<Module> obsoleteModules = new HashSet<Module>();

  public MavenToIdeaMapping(MavenProjectModel mavenProjectModel, String moduleDir, Project project) {
    resolveModuleNames(mavenProjectModel);

    resolveModulePaths(mavenProjectModel, moduleDir);

    if (project != null) {
      mapToExistingModules(mavenProjectModel, project);
    }
  }

  private void resolveModuleNames(final MavenProjectModel mavenProjectModel) {
    final List<String> duplicateNames = collectDuplicateModuleNames(mavenProjectModel);

    mavenProjectModel.visit(new MavenProjectModel.PlainNodeVisitor() {
      public void visit(MavenProjectModel.Node node) {
        MavenId id = node.getMavenId();
        String name = node.getLinkedModule() != null
                      ? node.getLinkedModule().getName()
                      : generateModuleName(id, duplicateNames);


        projectNodeToModuleName.put(node, name);

        libraryNameToModuleName.put(getLibraryName(id), name);

        projectNames.add(name);

        ProjectId projectId = node.getProjectId();
        projectIdToModuleName.put(projectId, name);
        projectIdToFile.put(projectId, node.getFile());
      }
    });
  }

  private List<String> collectDuplicateModuleNames(MavenProjectModel m) {
    final List<String> allNames = new ArrayList<String>();
    final List<String> result = new ArrayList<String>();

    m.visit(new MavenProjectModel.PlainNodeVisitor() {
      public void visit(MavenProjectModel.Node node) {
        String name = node.getMavenId().artifactId;
        if (allNames.contains(name)) result.add(name);
        allNames.add(name);
      }
    });

    return result;
  }

  private void resolveModulePaths(final MavenProjectModel mavenProjectModel, final String dedicatedModuleDir) {
    mavenProjectModel.visit(new MavenProjectModel.PlainNodeVisitor() {
      public void visit(final MavenProjectModel.Node node) {
        final Module module = node.getLinkedModule();
        projectToModulePath.put(node, module != null ? module.getModuleFilePath() : generateModulePath(node, dedicatedModuleDir));
      }
    });
  }

  private void mapToExistingModules(final MavenProjectModel mavenProjectModel, final Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      nameToModule.put(module.getName(), module);
    }

    mavenProjectModel.visit(new MavenProjectModel.PlainNodeVisitor() {
      public void visit(MavenProjectModel.Node node) {
        Module module = node.getLinkedModule() != null
                        ? node.getLinkedModule()
                        : nameToModule.get(projectNodeToModuleName.get(node));
        if (module == null) return;

        String modulePath = FileUtil.toSystemIndependentName(module.getModuleFilePath());
        String nodePath = FileUtil.toSystemIndependentName(getModuleFilePath(node));

        if (modulePath.equalsIgnoreCase(nodePath)) {
          projectToModule.put(node, module);
          obsoleteModules.remove(module);
        }
        else {
          obsoleteModules.add(module);
          node.unlinkModule();
        }
      }
    });

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (projectNames.contains(module.getName())) continue;
      obsoleteModules.add(module);
    }

    for (Module obsoleteModule : obsoleteModules) {
      nameToModule.remove(obsoleteModule.getName());
    }
  }

  private String generateModuleName(MavenId id, List<String> duplicateNames) {
    String name = id.artifactId;
    if (duplicateNames.contains(name)) name = name + " (" + id.groupId + ")";
    return name;
  }

  private String generateModulePath(MavenProjectModel.Node node, String dedicatedModuleDir) {
    return new File(StringUtil.isEmptyOrSpaces(dedicatedModuleDir) ? node.getDirectory() : dedicatedModuleDir,
                    projectNodeToModuleName.get(node) + IML_EXT).getPath();
  }

  public Map<ProjectId, VirtualFile> getProjectMapping() {
    return projectIdToFile;
  }

  public String getLibraryName(MavenId id) {
    return id.toString();
  }

  public String getModuleName(ProjectId id) {
    String name = projectIdToModuleName.get(id);
    if (nameToModule.containsKey(name) || projectNames.contains(name)) return name;
    return null;
  }

  public Collection<ProjectId> getProjectIds() {
    return projectIdToModuleName.keySet();
  }

  public Collection<Module> getExistingModules() {
    return nameToModule.values();
  }

  public Module getModule(MavenProjectModel.Node node) {
    return projectToModule.get(node);
  }

  public String getModuleFilePath(MavenProjectModel.Node node) {
    return projectToModulePath.get(node);
  }

  public List<Module> getObsoleteModules() {
    return new ArrayList<Module>(obsoleteModules);
  }

  public Map<String, String> getLibraryNameToModuleName() {
    return libraryNameToModuleName;
  }
}