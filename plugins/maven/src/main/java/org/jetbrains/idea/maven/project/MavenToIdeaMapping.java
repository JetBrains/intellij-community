package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.core.util.MavenId;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenToIdeaMapping {

  final private Map<MavenId, String> projectIdToModuleName = new HashMap<MavenId, String>();

  final private Set<String> projectNames = new HashSet<String>();

  final private Map<MavenProjectModel.Node, String> projectToModuleName = new HashMap<MavenProjectModel.Node, String>();

  final private Map<MavenProjectModel.Node, String> projectToModulePath = new HashMap<MavenProjectModel.Node, String>();

  final private Map<String, Module> nameToModule = new HashMap<String, Module>();

  final private Map<String, String> libraryNameToModuleName = new HashMap<String, String>();

  final private Map<MavenProjectModel.Node, Module> projectToModule = new HashMap<MavenProjectModel.Node, Module>();

  final private Set<Module> obsoleteModules = new HashSet<Module>();

  @NonNls private static final String IML_EXT = ".iml";

  public MavenToIdeaMapping(MavenProjectModel mavenProjectModel, String moduleDir, Project project) {
    resolveModuleNames(mavenProjectModel);

    resolveModulePaths(mavenProjectModel, moduleDir);

    if (project != null) {
      mapToExistingModules(mavenProjectModel, project);
    }
  }

  private void resolveModuleNames(final MavenProjectModel mavenProjectModel) {
    final List<String> duplicateNames = collectDuplicateModuleNames(mavenProjectModel);

    mavenProjectModel.visit(new MavenProjectModel.MavenProjectVisitorPlain() {
      public void visit(MavenProjectModel.Node node) {
        final MavenId id = node.getId();
        final String name =
          node.getLinkedModule() != null ? node.getLinkedModule().getName() : generateModuleName(id, duplicateNames);

        projectIdToModuleName.put(id, name);

        projectToModuleName.put(node, name);

        libraryNameToModuleName.put(getLibraryName(id), name);

        projectNames.add(name);
      }
    });
  }

  private List<String> collectDuplicateModuleNames(MavenProjectModel m) {
    final List<String> allNames = new ArrayList<String>();
    final List<String> result = new ArrayList<String>();

    m.visit(new MavenProjectModel.MavenProjectVisitorPlain() {
      public void visit(MavenProjectModel.Node node) {
        String name = node.getId().artifactId;
        if (allNames.contains(name)) result.add(name);
        allNames.add(name);
      }
    });

    return result;
  }

  private void resolveModulePaths(final MavenProjectModel mavenProjectModel, final String dedicatedModuleDir) {
    mavenProjectModel.visit(new MavenProjectModel.MavenProjectVisitorPlain() {
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

    mavenProjectModel.visit(new MavenProjectModel.MavenProjectVisitorPlain() {
      public void visit(MavenProjectModel.Node node) {
        Module module = node.getLinkedModule() != null
                        ? node.getLinkedModule()
                        : nameToModule.get(projectToModuleName.get(node));
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

  private String generateModulePath(final MavenProjectModel.Node node, final String dedicatedModuleDir) {
    return new File(StringUtil.isEmptyOrSpaces(dedicatedModuleDir) ? node.getDirectory() : dedicatedModuleDir,
                    projectToModuleName.get(node) + IML_EXT).getPath();
  }

  public String getLibraryName(MavenId id) {
    return id.toString();
  }

  public String getModuleName(Artifact a) {
    // HACK!!!
    // we should find module by version range version, since it might be X-SNAPSHOT or SNAPSHOT
    // which is resolved in X-timestamp-build. But mapping contains base artefact versions.
    // todo: user MavenArtifactResolver instread.

    VersionRange range = a.getVersionRange();
    String version = range == null ? a.getVersion() : range.toString();
    MavenId versionId = new MavenId(a.getGroupId(), a.getArtifactId(), version);

    return getModuleName(versionId);
  }

  private String getModuleName(MavenId id) {
    String name = projectIdToModuleName.get(id);
    if (nameToModule.containsKey(name) || projectNames.contains(name)) return name;
    return null;
  }

  public Collection<MavenId> getMappedToModules() {
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

  public Collection<Module> getObsoleteModules() {
    return obsoleteModules;
  }

  public Map<String, String> getLibraryNameToModuleName() {
    return libraryNameToModuleName;
  }
}