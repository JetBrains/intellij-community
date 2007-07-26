package org.jetbrains.idea.maven.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleCircularDependencyException;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.core.util.Strings;

import java.io.File;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenToIdeaConverter {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.project.MavenToIdeaConverter");

  @NonNls public static final String JAR_TYPE = "jar";
  @NonNls public static final String JAVADOC_CLASSIFIER = "javadoc";
  @NonNls public static final String SOURCES_CLASSIFIER = "sources";

  @NonNls private static final String TARGET = "target";
  @NonNls private static final String GENERATED_SOURCES = "generated-sources";

  private final static String JAR_PREFIX = JarFileSystem.PROTOCOL + "://";

  private static Map<String, LanguageLevel> stringToLanguageLevel;

  public static void convert(final ModifiableModuleModel modifiableModel,
                             final MavenProjectModel projectModel,
                             final Collection<String> profiles,
                             final MavenToIdeaMapping mapping,
                             final MavenImporterPreferences preferences,
                             final boolean markSynthetic) {

    final MavenToIdeaConverter mavenToIdeaConverter = new MavenToIdeaConverter(modifiableModel, mapping, preferences, markSynthetic);

    for (MavenProjectModel.Node project : sortProjectsByDependencies(projectModel)) {
      mavenToIdeaConverter.convert(project, profiles);
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
  final private Pattern ignorePattern;

  private MavenToIdeaConverter(ModifiableModuleModel model,
                               final MavenToIdeaMapping mavenToIdeaMapping,
                               final MavenImporterPreferences preferences,
                               final boolean markSynthetic) {
    this.modifiableModuleModel = model;
    this.mavenToIdeaMapping = mavenToIdeaMapping;
    this.preferences = preferences;
    this.markSynthetic = markSynthetic;
    this.ignorePattern = Pattern.compile(Strings.translateMasks(preferences.getIgnoredDependencies()));
  }

  public void convert(MavenProjectModel.Node node, Collection<String> profiles) {
    final MavenProject mavenProject = node.getMavenProject();

    Module module = mavenToIdeaMapping.getModule(node);
    if (module == null) {
      module = modifiableModuleModel.newModule(mavenToIdeaMapping.getModuleFilePath(node));
    }

    convertRootModel(module, mavenProject, profiles);

    createFacets(module, mavenProject);

    SyntheticModuleUtil.setSynthetic(module, markSynthetic && !node.isLinked());
  }

  void convertRootModel(Module module, MavenProject mavenProject, final Collection<String> profiles) {
    RootModelAdapter rootModel = new RootModelAdapter(module);
    rootModel.init(mavenProject.getFile().getParent());
    createRoots(rootModel, mavenProject);
    createOutput(rootModel, mavenProject);
    createDependencies(rootModel, mavenProject);
    rootModel.setLanguageLevel(getLanguageLevel(getLanguageLevel(mavenProject, profiles)));
    rootModel.commit();
  }

  private void createFacets(Module module, MavenProject mavenProject) {
    final String packaging = mavenProject.getPackaging();
    if (!packaging.equals("jar")) {
      for (PackagingConverter converter : Extensions.getExtensions(PackagingConverter.EXTENSION_POINT_NAME)) {
        if (converter.isApplicable(packaging)) {
          converter.convert(module, mavenProject, mavenToIdeaMapping, modifiableModuleModel);
        }
      }
    }
  }

  private String getLanguageLevel(MavenProject mavenProject, final Collection<String> profiles) {
    for (Plugin plugin : ProjectUtil.collectPlugins(mavenProject, profiles, new HashMap<Plugin, MavenProject>()).keySet()) {
      if (plugin.getGroupId().equals("org.apache.maven.plugins") && plugin.getArtifactId().equals("maven-compiler-plugin")) {
        final Xpp3Dom configuration = (Xpp3Dom)plugin.getConfiguration();
        if (configuration != null) {
          final Xpp3Dom source = configuration.getChild("source");
          if (source != null) {
            return source.getValue();
          }
        }
        break;
      }
    }
    return null;
  }

  private static void createRoots(final RootModelAdapter rootModel, final MavenProject mavenProject) {
    createSourceRoots(rootModel, mavenProject);
    createGeneratedSourceRoots(rootModel, mavenProject);
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

  private static void createGeneratedSourceRoots(RootModelAdapter rootModel, MavenProject mavenProject) {
    // TODO: do this properly
    final File targetDir = new File(mavenProject.getFile().getParent(), TARGET);
    if (targetDir.isDirectory()) {
      for (File file : targetDir.listFiles()) {
        if (file.isDirectory()) {
          if (file.getName().equals(GENERATED_SOURCES)) {
            for (File genSrcDir : file.listFiles()) {
              rootModel.createSrcDir(genSrcDir.getPath(), false);
            }
          }
          else {
            rootModel.excludeRoot(file.getPath());
          }
        }
      }
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
      if(ignorePattern.matcher(id.toString()).matches()){
        continue;
      }
      final String moduleName = mavenToIdeaMapping.getModuleName(id);
      if (moduleName != null) {
        rootModel.createModuleDependency(moduleName);
      }
      else {
        final String artifactPath = artifact.getFile().getPath();
        rootModel.createModuleLibrary(mavenToIdeaMapping.getLibraryName(id), getUrl(artifactPath, null),
                                      getUrl(artifactPath, SOURCES_CLASSIFIER), getUrl(artifactPath, JAVADOC_CLASSIFIER));
      }
    }
  }

  static void updateModel(Module module, MavenProject mavenProject) {
    RootModelAdapter rootModel = new RootModelAdapter(module);
    rootModel.resetRoots();
    createRoots(rootModel, mavenProject);
    updateSourcesAndJavadoc(rootModel);
    rootModel.commit();
  }

  private static void updateSourcesAndJavadoc(final RootModelAdapter rootModel) {
    for (Map.Entry<String, String> entry : rootModel.getModuleLibraries().entrySet()) {
      final String url = entry.getValue();
      if (url.startsWith(JAR_PREFIX) && url.endsWith(JarFileSystem.JAR_SEPARATOR)) {
        final String path = url.substring(JAR_PREFIX.length(), url.lastIndexOf(JarFileSystem.JAR_SEPARATOR));
        rootModel.updateModuleLibrary(entry.getKey(), getUrl(path, SOURCES_CLASSIFIER), getUrl(path, JAVADOC_CLASSIFIER));
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

  private static String getUrl(final String artifactPath, final String classifier) {
    String path = artifactPath;
    if (classifier != null) {
      path = MessageFormat.format("{0}-{1}.jar", path.substring(0, path.lastIndexOf(".")), classifier);
      if (!new File(path).exists()) {
        return null;
      }
    }
    return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, path) + JarFileSystem.JAR_SEPARATOR;
  }

  public static LanguageLevel getLanguageLevel(final String level) {
    if(stringToLanguageLevel==null){
      stringToLanguageLevel = new HashMap<String, LanguageLevel>();
      stringToLanguageLevel.put("1.3", LanguageLevel.JDK_1_3);
      stringToLanguageLevel.put("1.4", LanguageLevel.JDK_1_4);
      stringToLanguageLevel.put("1.5", LanguageLevel.JDK_1_5);
    }
    return stringToLanguageLevel.get(level);
  }
}
