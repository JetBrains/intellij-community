package org.jetbrains.idea.maven.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleCircularDependencyException;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.core.util.MavenId;

import java.io.File;
import java.text.MessageFormat;
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenToIdeaConverter {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.project.MavenToIdeaConverter");

  @NonNls public static final String JAR_TYPE = "jar";
  @NonNls public static final String JAVADOC_CLASSIFIER = "javadoc";
  @NonNls public static final String SOURCES_CLASSIFIER = "sources";

  public static void convert(final ModifiableModuleModel modifiableModel,
                             final MavenProjectModel projectModel,
                             final MavenToIdeaMapping mapping,
                             final MavenImporterPreferences preferences,
                             final boolean markSynthetic) {

    final MavenToIdeaConverter mavenToIdeaConverter = new MavenToIdeaConverter(modifiableModel, mapping, preferences, markSynthetic);

    for (MavenProjectModel.Node project : sortProjectsByDependencies(projectModel)) {
      mavenToIdeaConverter.convert(project);
    }

    createModuleGroups(modifiableModel, projectModel, mapping, preferences.isCreateModuleGroups());

    for (Module module : mapping.getExistingModules()) {
      new RootModelAdapter(module).resolveModuleDependencies(mapping.getLibraryNameToModuleName());
    }

    try {
      modifiableModel.commit();
    }
    catch (ModuleCircularDependencyException ignore) {
    }
  }

  private static List<MavenProjectModel.Node> sortProjectsByDependencies(final MavenProjectModel projectModel) {
    final List<MavenProjectModel.Node> projects = new ArrayList<MavenProjectModel.Node>();
    projectModel.visit(new MavenProjectModel.MavenProjectVisitorPlain() {
      public void visit(final MavenProjectModel.Node node) {
        projects.add(node);
      }
    });

    // Dumb implementation just puts all EAR modules after all others
    // TODO replace with proper topo sort on dependencies
    Collections.sort(projects, new Comparator<MavenProjectModel.Node>() {
      public int compare(final MavenProjectModel.Node o1, final MavenProjectModel.Node o2) {
        final boolean isEar1 = o1.getMavenProject().getPackaging().equalsIgnoreCase("ear");
        final boolean isEar2 = o2.getMavenProject().getPackaging().equalsIgnoreCase("ear");
        return !isEar1 && isEar2 ? -1 : isEar1 && !isEar2 ? 1 : 0;
      }
    });
    return projects;
  }

  private static void createModuleGroups(final ModifiableModuleModel modifiableModel,
                                         final MavenProjectModel projectModel,
                                         final MavenToIdeaMapping mapping,
                                         final boolean createModuleGroups) {
    final Stack<String> groups = new Stack<String>();
    projectModel.visit(new MavenProjectModel.MavenProjectVisitorPlain() {
      public void visit(final MavenProjectModel.Node node) {
        final String name = mapping.getModuleName(node.getId());
        LOG.assertTrue(name != null);

        if (createModuleGroups && !node.mavenModules.isEmpty()) {
          groups.push(ProjectBundle.message("module.group.name", name));
        }

        final Module module = modifiableModel.findModuleByName(name);
        if (module != null) {
          modifiableModel.setModuleGroupPath(module, groups.isEmpty() ? null : groups.toArray(new String[groups.size()]));
        }
        else {
          LOG.warn("Cannot find module " + name);
        }
      }

      public void leave(MavenProjectModel.Node node) {
        if (createModuleGroups && !node.mavenModules.isEmpty()) {
          groups.pop();
        }
      }
    });
  }

  final private ModifiableModuleModel modifiableModuleModel;
  final private MavenToIdeaMapping mavenToIdeaMapping;
  final private MavenImporterPreferences preferences;
  final private boolean markSynthetic;

  private MavenToIdeaConverter(ModifiableModuleModel model,
                               final MavenToIdeaMapping mavenToIdeaMapping,
                               final MavenImporterPreferences preferences,
                               final boolean markSynthetic) {
    this.markSynthetic = markSynthetic;
    this.mavenToIdeaMapping = mavenToIdeaMapping;
    this.modifiableModuleModel = model;
    this.preferences = preferences;
  }

  public void convert(MavenProjectModel.Node node) {
    Module module = mavenToIdeaMapping.getModule(node);
    if (module == null) {
      module = modifiableModuleModel.newModule(mavenToIdeaMapping.getModuleFilePath(node));
    }

    convertRootModel(module, node);

    createFacets(module, node);

    SyntheticModuleUtil.setSynthetic(module, markSynthetic && !node.isLinked());
  }

  void convertRootModel(Module module, MavenProjectModel.Node node) {
    RootModelAdapter rootModel = new RootModelAdapter(module);
    rootModel.resetRoots(node.getDirectory());

    // TODO: do this properly
    rootModel.createSrcDir(new File(node.getDirectory(), "target/generated-sources/modello").getPath(), false);
    rootModel.createSrcDir(new File(node.getDirectory(), "target/generated-sources/antlr").getPath(), false);

    final MavenProject mavenProject = node.getMavenProject();
    createSourceRoots(rootModel, mavenProject);
    createOutput(rootModel, mavenProject);
    createDependencies(rootModel, mavenProject);

    rootModel.commit();
  }

  private void createFacets(final Module module, final MavenProjectModel.Node node) {
    final String packaging = node.getMavenProject().getPackaging();
    if (!packaging.equals("jar")) {
      for (PackagingConverter converter : Extensions.getExtensions(PackagingConverter.EXTENSION_POINT_NAME)) {
        if (converter.isApplicable(packaging)) {
          converter.convert(module, node, mavenToIdeaMapping, modifiableModuleModel);
        }
      }
    }
  }

  static void createSourceRoots(RootModelAdapter rootModel, MavenProject mavenProject) {
    for (Object o : mavenProject.getCompileSourceRoots()) {
      rootModel.createSrcDir((String)o, false);
    }
    for (Object o : mavenProject.getTestCompileSourceRoots()) {
      rootModel.createSrcDir((String)o, true);
    }

    for (Object o : mavenProject.getResources()) {
      rootModel.createSrcDir(((Resource)o).getDirectory(), false);
    }
    for (Object o : mavenProject.getTestResources()) {
      rootModel.createSrcDir(((Resource)o).getDirectory(), true);
    }
  }

  private void createOutput(final RootModelAdapter rootModel, final MavenProject mavenProject) {
    Build build = mavenProject.getBuild();
    if (preferences.isUseMavenOutput()) {
      rootModel.useModuleOutput(build.getOutputDirectory(), build.getTestOutputDirectory());
    }
    else {
      rootModel.useProjectOutput();
      rootModel.excludeRoot(build.getOutputDirectory());
      rootModel.excludeRoot(build.getTestOutputDirectory());
    }
  }

  void createDependencies(RootModelAdapter rootModel, MavenProject mavenProject) {
    for (Artifact artifact : extractDependencies(mavenProject)) {
      MavenId id = new MavenId(artifact);
      final String moduleName = mavenToIdeaMapping.getModuleName(id);
      if (moduleName != null) {
        rootModel.createModuleDependency(moduleName);
      }
      else {
        rootModel.createModuleLibrary(mavenToIdeaMapping.getLibraryName(id), getUrl(artifact, null), getUrl(artifact, SOURCES_CLASSIFIER),
                                      getUrl(artifact, JAVADOC_CLASSIFIER));
      }
    }
  }

  private static List<Artifact> extractDependencies(final MavenProject mavenProject) {
    Map<String, Artifact> projectIdToArtifact = new TreeMap<String, Artifact>();
    for (Object o : mavenProject.getArtifacts()) {
      Artifact newArtifact = (Artifact)o;
      if (newArtifact.getType().equalsIgnoreCase(JAR_TYPE)) {
        String projectId = newArtifact.getGroupId() + ":" + newArtifact.getArtifactId();
        Artifact oldArtifact = projectIdToArtifact.get(projectId);
        if (oldArtifact == null ||
            new DefaultArtifactVersion(oldArtifact.getVersion()).compareTo(new DefaultArtifactVersion(newArtifact.getVersion())) < 0) {
          projectIdToArtifact.put(projectId, newArtifact);
        }
      }
    }
    return new ArrayList<Artifact>(projectIdToArtifact.values());
  }

  private static String getUrl(final Artifact artifact, final String classifier) {
    String path = artifact.getFile().getPath();
    if (classifier != null) {
      path = MessageFormat.format("{0}-{1}.{2}", path.substring(0, path.lastIndexOf(".")), classifier, artifact.getType());
      if (!new File(path).exists()) {
        return null;
      }
    }
    return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, path) + JarFileSystem.JAR_SEPARATOR;
  }
}
