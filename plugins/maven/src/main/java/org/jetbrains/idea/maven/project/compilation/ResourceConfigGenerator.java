// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.compilation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.UnsyncByteArrayOutputStream;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.references.MavenFilteredPropertyPsiReferenceProvider;
import org.jetbrains.idea.maven.importing.MavenImportUtil;
import org.jetbrains.idea.maven.importing.StandardMavenModuleType;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenResource;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.server.RemotePathTransformerFactory;
import org.jetbrains.idea.maven.utils.ManifestBuilder;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.jps.maven.model.impl.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.jetbrains.idea.maven.importing.MavenImportUtil.MAIN_SUFFIX;
import static org.jetbrains.idea.maven.importing.MavenImportUtil.TEST_SUFFIX;

public class ResourceConfigGenerator {
  private static final Logger LOG = Logger.getInstance(ResourceConfigGenerator.class);
  private static final String IDEA_MAVEN_DISABLE_MANIFEST = System.getProperty("idea.maven.disable.manifest");

  private final ProjectFileIndex fileIndex;
  private final MavenProjectsManager mavenProjectsManager;
  private final RemotePathTransformerFactory.Transformer transformer;
  private final MavenProjectConfiguration projectConfig;
  private final MavenProject mavenProject;

  public ResourceConfigGenerator(ProjectFileIndex fileIndex,
                                 MavenProjectsManager mavenProjectsManager,
                                 RemotePathTransformerFactory.Transformer transformer,
                                 MavenProjectConfiguration projectConfig,
                                 MavenProject mavenProject) {
    this.fileIndex = fileIndex;
    this.mavenProjectsManager = mavenProjectsManager;
    this.transformer = transformer;
    this.projectConfig = projectConfig;
    this.mavenProject = mavenProject;
  }

  public void generateResourceConfig() {
    // do not add resource roots for 'pom' packaging projects
    if ("pom".equals(mavenProject.getPackaging())) return;

    VirtualFile pomXml = mavenProject.getFile();
    Module module = fileIndex.getModuleForFile(pomXml);
    if (module == null) return;

    if (!Comparing.equal(mavenProject.getDirectoryFile(), fileIndex.getContentRootForFile(pomXml))) return;

    var javaVersions = MavenImportUtil.getMavenJavaVersions(mavenProject);
    var moduleType = MavenImportUtil.getModuleType(mavenProject, javaVersions);

    generate(module, moduleType);

    if (moduleType == StandardMavenModuleType.COMPOUND_MODULE) {
      var moduleManager = ModuleManager.getInstance(module.getProject());
      var moduleName = module.getName();

      generate(moduleManager.findModuleByName(moduleName + MAIN_SUFFIX), StandardMavenModuleType.MAIN_ONLY);
      generate(moduleManager.findModuleByName(moduleName + TEST_SUFFIX), StandardMavenModuleType.TEST_ONLY);
    }
  }

  private void generate(com.intellij.openapi.module.Module module, StandardMavenModuleType moduleType) {
    if (module == null) return;

    MavenModuleResourceConfiguration resourceConfig = new MavenModuleResourceConfiguration();
    MavenId projectId = mavenProject.getMavenId();
    resourceConfig.id = new MavenIdBean(projectId.getGroupId(), projectId.getArtifactId(), projectId.getVersion());

    MavenId parentId = mavenProject.getParentId();
    if (parentId != null) {
      resourceConfig.parentId = new MavenIdBean(parentId.getGroupId(), parentId.getArtifactId(), parentId.getVersion());
    }
    resourceConfig.directory = transformer.toRemotePathOrSelf(FileUtil.toSystemIndependentName(mavenProject.getDirectory()));
    resourceConfig.delimitersPattern = MavenFilteredPropertyPsiReferenceProvider.getDelimitersPattern(mavenProject).pattern();
    for (Map.Entry<String, String> entry : mavenProject.getModelMap().entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (value != null) {
        resourceConfig.modelMap.put(key, transformer.toRemotePathOrSelf(value));
      }
    }

    addEarModelMapEntries(mavenProject, resourceConfig.modelMap);

    Element pluginConfiguration = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin");
    resourceConfig.outputDirectory =
      transformer.toRemotePathOrSelf(getResourcesPluginGoalOutputDirectory(mavenProject, pluginConfiguration, "resources"));
    resourceConfig.testOutputDirectory =
      transformer.toRemotePathOrSelf(getResourcesPluginGoalOutputDirectory(mavenProject, pluginConfiguration, "testResources"));

    if (moduleType != StandardMavenModuleType.TEST_ONLY) {
      addResources(transformer, resourceConfig.resources, mavenProject.getResources());
    }

    if (moduleType != StandardMavenModuleType.MAIN_ONLY) {
      addResources(transformer, resourceConfig.testResources, mavenProject.getTestResources());
    }

    addWebResources(transformer, module, projectConfig, mavenProject);
    addEjbClientArtifactConfiguration(module, projectConfig, mavenProject);

    resourceConfig.filteringExclusions.addAll(MavenProjectsTree.getFilterExclusions(mavenProject));

    final Properties properties = getFilteringProperties(mavenProject, mavenProjectsManager);
    for (Map.Entry<Object, Object> propEntry : properties.entrySet()) {
      resourceConfig.properties.put((String)propEntry.getKey(), transformer.toRemotePathOrSelf((String)propEntry.getValue()));
    }

    resourceConfig.escapeString = MavenJDOMUtil.findChildValueByPath(pluginConfiguration, "escapeString", null);
    String escapeWindowsPaths = MavenJDOMUtil.findChildValueByPath(pluginConfiguration, "escapeWindowsPaths");
    if (escapeWindowsPaths != null) {
      resourceConfig.escapeWindowsPaths = Boolean.parseBoolean(escapeWindowsPaths);
    }

    String overwrite = MavenJDOMUtil.findChildValueByPath(pluginConfiguration, "overwrite");
    if (overwrite != null) {
      resourceConfig.overwrite = Boolean.parseBoolean(overwrite);
    }

    projectConfig.moduleConfigurations.put(module.getName(), resourceConfig);
    generateManifest(mavenProject, module, resourceConfig);
  }

  private static void addEarModelMapEntries(@NotNull MavenProject mavenProject, @NotNull Map<String, String> modelMap) {
    Element pluginConfiguration = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-ear-plugin");
    final String skinnyWars = MavenJDOMUtil.findChildValueByPath(pluginConfiguration, "skinnyWars", "false");
    modelMap.put("build.plugin.maven-ear-plugin.skinnyWars", skinnyWars);
  }

  @Nullable
  private static String getResourcesPluginGoalOutputDirectory(@NotNull MavenProject mavenProject,
                                                              @Nullable Element pluginConfiguration,
                                                              @NotNull String goal) {
    final Element goalConfiguration = mavenProject.getPluginGoalConfiguration("org.apache.maven.plugins", "maven-resources-plugin", goal);
    String outputDirectory = MavenJDOMUtil.findChildValueByPath(goalConfiguration, "outputDirectory", null);
    if (outputDirectory == null) {
      outputDirectory = MavenJDOMUtil.findChildValueByPath(pluginConfiguration, "outputDirectory", null);
    }
    return outputDirectory == null || FileUtil.isAbsolute(outputDirectory)
           ? outputDirectory
           : mavenProject.getDirectory() + '/' + outputDirectory;
  }

  private static void generateManifest(@NotNull MavenProject mavenProject,
                                       @NotNull com.intellij.openapi.module.Module module,
                                       @NotNull MavenModuleResourceConfiguration resourceConfig) {
    if (mavenProject.isAggregator()) return;
    if (Boolean.parseBoolean(IDEA_MAVEN_DISABLE_MANIFEST)) {
      resourceConfig.manifest = null;
      return;
    }

    try {
      String jdkVersion = null;
      Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk != null && (jdkVersion = sdk.getVersionString()) != null) {
        final int quoteIndex = jdkVersion.indexOf('"');
        if (quoteIndex != -1) {
          jdkVersion = jdkVersion.substring(quoteIndex + 1, jdkVersion.length() - 1);
        }
      }

      MavenDomProjectModel domModel = MavenDomUtil.getMavenDomProjectModel(module.getProject(), mavenProject.getFile());
      if (domModel != null) {
        UnsyncByteArrayOutputStream outputStream = new UnsyncByteArrayOutputStream();
        new ManifestBuilder(mavenProject).withJdkVersion(jdkVersion).build().write(outputStream);
        String resolvedText = MavenPropertyResolver.resolve(outputStream.toString(), domModel);
        resourceConfig.manifest = Base64.getEncoder().encodeToString(resolvedText.getBytes(StandardCharsets.UTF_8));
      }
      resourceConfig.classpath = ManifestBuilder.getClasspath(mavenProject);
    }
    catch (ManifestBuilder.ManifestBuilderException e) {
      LOG.warn("Unable to generate artifact manifest", e);
    }
    catch (Exception e) {
      LOG.warn("Unable to save generated artifact manifest", e);
    }
  }

  private static Properties getFilteringProperties(MavenProject mavenProject,
                                                   MavenProjectsManager mavenProjectsManager) {
    final Properties properties = new Properties();

    for (String each : mavenProject.getFilterPropertiesFiles()) {
      try (FileInputStream in = new FileInputStream(each)) {
        properties.load(in);
      }
      catch (IOException ignored) {
      }
    }

    properties.putAll(mavenProject.getProperties());

    properties.setProperty("settings.localRepository", mavenProject.getLocalRepositoryPath().toAbsolutePath().toString());

    String jreDir = MavenUtil.getModuleJreHome(mavenProjectsManager, mavenProject);
    if (jreDir != null) {
      properties.setProperty("java.home", jreDir);
    }

    String javaVersion = MavenUtil.getModuleJavaVersion(mavenProjectsManager, mavenProject);
    if (javaVersion != null) {
      properties.setProperty("java.version", javaVersion);
    }

    return properties;
  }

  private static void addResources(RemotePathTransformerFactory.Transformer transformer,
                                   final List<ResourceRootConfiguration> container,
                                   @NotNull Collection<MavenResource> resources) {

    for (MavenResource resource : resources) {
      final String dir = resource.getDirectory();

      final ResourceRootConfiguration props = new ResourceRootConfiguration();
      props.directory = transformer.toRemotePathOrSelf(FileUtil.toSystemIndependentName(dir));

      final String target = transformer.toRemotePathOrSelf(resource.getTargetPath());
      props.targetPath = target != null ? FileUtil.toSystemIndependentName(target) : null;

      props.isFiltered = resource.isFiltered();
      props.includes.clear();
      for (String include : resource.getIncludes()) {
        props.includes.add(include.trim());
      }
      props.excludes.clear();
      for (String exclude : resource.getExcludes()) {
        props.excludes.add(exclude.trim());
      }
      container.add(props);
    }
  }

  private static void addWebResources(RemotePathTransformerFactory.Transformer transformer,
                                      @NotNull com.intellij.openapi.module.Module module,
                                      MavenProjectConfiguration projectCfg,
                                      MavenProject mavenProject) {
    Element warCfg = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-war-plugin");
    if (warCfg == null) return;

    boolean filterWebXml = Boolean.parseBoolean(warCfg.getChildTextTrim("filteringDeploymentDescriptors"));
    Element webResources = warCfg.getChild("webResources");

    String webArtifactName = MavenUtil.getArtifactName("war", module, true);

    MavenWebArtifactConfiguration artifactResourceCfg = projectCfg.webArtifactConfigs.get(webArtifactName);
    if (artifactResourceCfg == null) {
      artifactResourceCfg = new MavenWebArtifactConfiguration();
      artifactResourceCfg.moduleName = module.getName();
      projectCfg.webArtifactConfigs.put(webArtifactName, artifactResourceCfg);
    }
    else {
      LOG.error("MavenWebArtifactConfiguration already exists.");
    }

    addSplitAndTrimmed(artifactResourceCfg.packagingIncludes, warCfg.getChildTextTrim("packagingIncludes"));
    addSplitAndTrimmed(artifactResourceCfg.packagingExcludes, warCfg.getChildTextTrim("packagingExcludes"));
    addConfigValues(artifactResourceCfg.nonFilteredFileExtensions, "nonFilteredFileExtensions", "nonFilteredFileExtension", warCfg);

    String warSourceDirectory = warCfg.getChildTextTrim("warSourceDirectory");
    if (warSourceDirectory == null) warSourceDirectory = "src/main/webapp";
    if (!FileUtil.isAbsolute(warSourceDirectory)) {
      warSourceDirectory = mavenProject.getDirectory() + '/' + warSourceDirectory;
    }
    artifactResourceCfg.warSourceDirectory =
      transformer.toRemotePathOrSelf(FileUtil.toSystemIndependentName(StringUtil.trimEnd(warSourceDirectory, '/')));

    addSplitAndTrimmed(artifactResourceCfg.warSourceIncludes, warCfg.getChildTextTrim("warSourceIncludes"));
    addSplitAndTrimmed(artifactResourceCfg.warSourceExcludes, warCfg.getChildTextTrim("warSourceExcludes"));

    if (webResources != null) {
      for (Element resource : webResources.getChildren("resource")) {
        ResourceRootConfiguration r = new ResourceRootConfiguration();
        String directory = resource.getChildTextTrim("directory");
        if (StringUtil.isEmptyOrSpaces(directory)) continue;

        if (!FileUtil.isAbsolute(directory)) {
          directory = mavenProject.getDirectory() + '/' + directory;
        }

        r.directory = transformer.toRemotePathOrSelf(directory);
        r.isFiltered = Boolean.parseBoolean(resource.getChildTextTrim("filtering"));

        r.targetPath = resource.getChildTextTrim("targetPath");

        addConfigValues(r.includes, "includes", "include", resource);
        addConfigValues(r.excludes, "excludes", "exclude", resource);

        artifactResourceCfg.webResources.add(r);
      }
    }

    if (filterWebXml) {
      ResourceRootConfiguration r = new ResourceRootConfiguration();
      r.directory = transformer.toRemotePathOrSelf(warSourceDirectory);
      r.includes = Collections.singleton("WEB-INF/web.xml");
      r.isFiltered = true;
      r.targetPath = "";
      artifactResourceCfg.webResources.add(r);
    }
  }

  private static void addConfigValues(Collection<String> collection, String tag, String subTag, Element resource) {
    Element config = resource.getChild(tag);
    if (config != null) {
      for (Element value : config.getChildren(subTag)) {
        String text = value.getTextTrim();
        if (!text.isEmpty()) {
          collection.add(text);
        }
      }
      if (config.getChildren(subTag).isEmpty()) {
        addSplitAndTrimmed(collection, config.getTextTrim());
      }
    }
  }

  private static void addSplitAndTrimmed(Collection<String> collection, @Nullable String commaSeparatedList) {
    if (commaSeparatedList != null) {
      for (String s : StringUtil.split(commaSeparatedList, ",")) {
        collection.add(s.trim());
      }
    }
  }

  private static void addEjbClientArtifactConfiguration(Module module,
                                                        MavenProjectConfiguration projectCfg,
                                                        MavenProject mavenProject) {
    Element pluginCfg = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-ejb-plugin");

    if (pluginCfg == null || !Boolean.parseBoolean(pluginCfg.getChildTextTrim("generateClient"))) {
      return;
    }

    MavenEjbClientConfiguration ejbClientCfg = new MavenEjbClientConfiguration();

    Element includes = pluginCfg.getChild("clientIncludes");
    if (includes != null) {
      for (Element include : includes.getChildren("clientInclude")) {
        String includeText = include.getTextTrim();
        if (!includeText.isEmpty()) {
          ejbClientCfg.includes.add(includeText);
        }
      }
    }

    Element excludes = pluginCfg.getChild("clientExcludes");
    if (excludes != null) {
      for (Element exclude : excludes.getChildren("clientExclude")) {
        String excludeText = exclude.getTextTrim();
        if (!excludeText.isEmpty()) {
          ejbClientCfg.excludes.add(excludeText);
        }
      }
    }

    if (!ejbClientCfg.isEmpty()) {
      projectCfg.ejbClientArtifactConfigs.put(MavenUtil.getEjbClientArtifactName(module, true), ejbClientCfg);
    }
  }
}
