package org.jetbrains.idea.maven.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.core.util.Strings;

import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Pattern;


public class MavenToIdeaConverter {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.project.MavenToIdeaConverter");

  @NonNls public static final String JAR_TYPE = "jar";
  @NonNls public static final String JAVADOC_CLASSIFIER = "javadoc";
  @NonNls public static final String SOURCES_CLASSIFIER = "sources";

  private final static String JAR_PREFIX = JarFileSystem.PROTOCOL + "://";

  private ModifiableModuleModel myModuleModel;
  private MavenProjectModel myProjectModel;
  private MavenToIdeaMapping myMapping;
  private Collection<String> myProfiles;
  private MavenImporterPreferences myPrefs;
  private Pattern myIgnorePattern;

  private static Map<String, LanguageLevel> stringToLanguageLevel;

  public static void convert(ModifiableModuleModel moduleModel,
                             MavenProjectModel projectModel,
                             Collection<String> profiles,
                             MavenToIdeaMapping mapping,
                             MavenImporterPreferences prefs) {
    MavenToIdeaConverter c = new MavenToIdeaConverter(moduleModel, projectModel, mapping, profiles, prefs);
    c.convert();
  }

  private MavenToIdeaConverter(ModifiableModuleModel model,
                               MavenProjectModel projectModel,
                               MavenToIdeaMapping mapping,
                               Collection<String> profiles,
                               MavenImporterPreferences preferences) {
    myModuleModel = model;
    myProjectModel = projectModel;
    myMapping = mapping;
    myProfiles = profiles;
    myPrefs = preferences;
    myIgnorePattern = Pattern.compile(Strings.translateMasks(preferences.getIgnoredDependencies()));
  }

  private void convert() {
    convertModules();
    createModuleGroups();
    resolveDependenciesAndCommit();
  }

  private void convertModules() {
    myProjectModel.visit(new MavenProjectModel.MavenProjectVisitorRoot() {
      public void visit(final MavenProjectModel.Node node) {
        convertNode(node, myProfiles);
        for (MavenProjectModel.Node subnode : node.mavenModulesTopoSorted) {
          convertNode(subnode, myProfiles);
        }
      }
    });
  }

  private void convertNode(MavenProjectModel.Node node, Collection<String> profiles) {
    final MavenProject mavenProject = node.getMavenProject();

    Module module = myMapping.getModule(node);
    if (module == null) {
      module = myModuleModel.newModule(myMapping.getModuleFilePath(node));
    }

    convertRootModel(module, mavenProject);
    configureFacets(module, mavenProject, profiles);
  }

  private void convertRootModel(Module module, MavenProject mavenProject) {
    RootModelAdapter rootModel = new RootModelAdapter(module);
    rootModel.init(mavenProject.getFile().getParent());
    configFolders(rootModel, mavenProject);
    configDependencies(rootModel, mavenProject);
    rootModel.setLanguageLevel(getLanguageLevel(getLanguageLevel(mavenProject)));
    rootModel.commit();
  }

  private void configureFacets(Module module, MavenProject mavenProject, Collection<String> profiles) {
    for (FacetImporter importer : Extensions.getExtensions(FacetImporter.EXTENSION_POINT_NAME)) {
      if (importer.isApplicable(mavenProject, profiles)) {
        importer.process(module, mavenProject, myProfiles, myMapping, myModuleModel);
      }
    }
  }

  private void createModuleGroups() {
    final boolean createModuleGroups = myPrefs.isCreateModuleGroups();

    final Stack<String> groups = new Stack<String>();
    myProjectModel.visit(new MavenProjectModel.MavenProjectVisitorPlain() {
      public void visit(final MavenProjectModel.Node node) {
        final String name = myMapping.getModuleName(node.getId());
        LOG.assertTrue(name != null);

        if (createModuleGroups && !node.mavenModules.isEmpty()) {
          groups.push(ProjectBundle.message("module.group.name", name));
        }

        final Module module = myModuleModel.findModuleByName(name);
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

  @Nullable
  private String getLanguageLevel(MavenProject mavenProject) {
    return ProjectUtil.findPluginConfiguration(mavenProject,
                                               myProfiles,
                                               "org.apache.maven.plugins",
                                               "maven-compiler-plugin",
                                               "source");
  }

  private void configFolders(RootModelAdapter m, MavenProject p) {
    configSourceFolders(m, p);
    configFoldersUnderTargetDir(m, p);
    configBuildHelperPluginSources(m, p);
    configAntRunPluginSources(m, p);
    configOutputFolders(m, p);
  }

  private void configSourceFolders(RootModelAdapter m, MavenProject p) {
    for (Object o : p.getCompileSourceRoots()) {
      m.addSourceDir((String)o, false);
    }
    for (Object o : p.getTestCompileSourceRoots()) {
      m.addSourceDir((String)o, true);
    }

    for (Object o : p.getResources()) {
      m.addSourceDir(((Resource)o).getDirectory(), false);
    }
    for (Object o : p.getTestResources()) {
      m.addSourceDir(((Resource)o).getDirectory(), true);
    }
  }

  private void configFoldersUnderTargetDir(RootModelAdapter m, MavenProject p) {
    String path = p.getBuild().getDirectory();
    VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(path);
    if (dir == null) return;

    for (VirtualFile f : dir.getChildren()) {
      if (!f.isDirectory()) continue;
      if (FileUtil.pathsEqual(f.getName(), "generated-sources")) {
        addAllSubDirsAsSources(m, f);
      }
      else {
       m.excludeRoot(f.getPath());
      }
    }
  }

  private void addAllSubDirsAsSources(RootModelAdapter m, VirtualFile dir) {
    for (VirtualFile f : dir.getChildren()) {
      if (!f.isDirectory()) continue;
      m.addSourceDir(f.getPath(), false);
    }
  }

  private void configBuildHelperPluginSources(RootModelAdapter m, MavenProject p) {
    Plugin plugin = ProjectUtil.findPlugin(p, myProfiles, "org.codehaus.mojo", "build-helper-maven-plugin");
    if (plugin == null) return;

    for (PluginExecution e : (List<PluginExecution>)plugin.getExecutions()) {
      for (String goal : (List<String>)e.getGoals()) {
        Xpp3Dom config = (Xpp3Dom)e.getConfiguration();
        if (config == null) continue;
        
        if (goal.equals("add-source")) addBuildHelperPluginSource(m, config, false);
        if (goal.equals("add-test-source")) addBuildHelperPluginSource(m, config, true);
      }
    }
  }

  private void addBuildHelperPluginSource(RootModelAdapter m, Xpp3Dom config, boolean isTestSources) {
    Xpp3Dom sources = config.getChild("sources");
    if (sources == null) return;

    for (Xpp3Dom source : sources.getChildren("source")) {
      m.addSourceDir(source.getValue(), isTestSources);
    }
  }

  private void configAntRunPluginSources(RootModelAdapter m, MavenProject p) {
    Plugin plugin = ProjectUtil.findPlugin(p, myProfiles, "org.apache.maven.plugins", "maven-antrun-plugin");
    if (plugin == null) return;

    for (PluginExecution e : (List<PluginExecution>)plugin.getExecutions()) {
      Xpp3Dom config = (Xpp3Dom)e.getConfiguration();
      if (config == null) continue;

      Xpp3Dom src = config.getChild("sourceRoot");
      Xpp3Dom test = config.getChild("testSourceRoot");
      
      if (src != null) m.addSourceDir(src.getValue(), false);
      if (test != null) m.addSourceDir(test.getValue(), true);
    }
  }

  private void configOutputFolders(RootModelAdapter m, MavenProject p) {
    Build build = p.getBuild();

    if (myPrefs.isUseMavenOutput()) {
      m.useModuleOutput(build.getOutputDirectory(), build.getTestOutputDirectory());
    }
    else {
      m.useProjectOutput();
      m.excludeRoot(build.getOutputDirectory());
      m.excludeRoot(build.getTestOutputDirectory());
    }
  }

  private void configDependencies(RootModelAdapter m, MavenProject p) {
    for (Artifact artifact : ProjectUtil.extractDependencies(p)) {
      MavenId id = new MavenId(artifact);

      if (isIgnored(id)) continue;

      String moduleName = findModuleFor(artifact);
      if (moduleName != null) {
        m.createModuleDependency(moduleName);
      }
      else {
        String artifactPath = artifact.getFile().getPath();
        boolean isExportable = ProjectUtil.isExpartableDependency(artifact);
        m.createModuleLibrary(myMapping.getLibraryName(id),
                              getUrl(artifactPath, null),
                              getUrl(artifactPath, SOURCES_CLASSIFIER),
                              getUrl(artifactPath, JAVADOC_CLASSIFIER),
                              isExportable);
      }
    }
  }

  private boolean isIgnored(MavenId id) {
    return myIgnorePattern.matcher(id.toString()).matches();
  }

  private String findModuleFor(Artifact artifact) {
    // we should find module by base version, since it might be X-SNAPSHOT
    // which is resolved in X-timestamp-build. But mapping contains base artefact versions.
    MavenId baseVersionId = new MavenId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getBaseVersion());
    return myMapping.getModuleName(baseVersionId);
  }

  private static LanguageLevel getLanguageLevel(final String level) {
    if (stringToLanguageLevel == null) {
      stringToLanguageLevel = new HashMap<String, LanguageLevel>();
      stringToLanguageLevel.put("1.3", LanguageLevel.JDK_1_3);
      stringToLanguageLevel.put("1.4", LanguageLevel.JDK_1_4);
      stringToLanguageLevel.put("1.5", LanguageLevel.JDK_1_5);
      stringToLanguageLevel.put("1.6", LanguageLevel.JDK_1_5);
    }
    return stringToLanguageLevel.get(level);
  }

  public static void updateModel(Module module, MavenProject mavenProject) {
    RootModelAdapter rootModel = new RootModelAdapter(module);
    updateSourcesAndJavadoc(rootModel);
    rootModel.commit();
  }

  private static void updateSourcesAndJavadoc(RootModelAdapter rootModel) {
    for (Map.Entry<String, String> entry : rootModel.getModuleLibraries().entrySet()) {
      final String url = entry.getValue();
      if (url.startsWith(JAR_PREFIX) && url.endsWith(JarFileSystem.JAR_SEPARATOR)) {
        final String path = url.substring(JAR_PREFIX.length(), url.lastIndexOf(JarFileSystem.JAR_SEPARATOR));
        String key = entry.getKey();
        rootModel.updateModuleLibrary(key != null ? key : path, getUrl(path, SOURCES_CLASSIFIER), getUrl(path, JAVADOC_CLASSIFIER));
      }
    }
  }

  private static String getUrl(String artifactPath, String classifier) {
    String path = artifactPath;
    if (classifier != null) {
      path = MessageFormat.format("{0}-{1}.jar", path.substring(0, path.lastIndexOf(".")), classifier);
    }
    String normalizedPath = FileUtil.toSystemIndependentName(path);
    return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, normalizedPath) + JarFileSystem.JAR_SEPARATOR;
  }
}
