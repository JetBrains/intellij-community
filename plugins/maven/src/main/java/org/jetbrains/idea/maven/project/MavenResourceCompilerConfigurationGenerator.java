/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.project;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.JdomKt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.references.MavenFilteredPropertyPsiReferenceProvider;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenResource;
import org.jetbrains.idea.maven.utils.ManifestBuilder;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.jps.maven.model.impl.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class MavenResourceCompilerConfigurationGenerator {

  private static Logger LOG = Logger.getInstance(MavenResourceCompilerConfigurationGenerator.class);

  private static final Pattern SIMPLE_NEGATIVE_PATTERN = Pattern.compile("!\\?(\\*\\.\\w+)");
  private static final String IDEA_MAVEN_DISABLE_MANIFEST = System.getProperty("idea.maven.disable.manifest");

  private final Project myProject;

  private final MavenProjectsManager myMavenProjectsManager;

  private final MavenProjectsTree myProjectsTree;

  public MavenResourceCompilerConfigurationGenerator(Project project, MavenProjectsTree projectsTree) {
    myProject = project;
    myMavenProjectsManager = MavenProjectsManager.getInstance(project);
    myProjectsTree = projectsTree;
  }

  public void generateBuildConfiguration(boolean force) {
    if (!myMavenProjectsManager.isMavenizedProject()) {
      return;
    }

    final BuildManager buildManager = BuildManager.getInstance();
    final File projectSystemDir = buildManager.getProjectSystemDirectory(myProject);
    if (projectSystemDir == null) {
      return;
    }

    final File mavenConfigFile = new File(projectSystemDir, MavenProjectConfiguration.CONFIGURATION_FILE_RELATIVE_PATH);

    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);

    ProjectFileIndex fileIndex = projectRootManager.getFileIndex();

    final int projectRootModificationCount = (int)projectRootManager.getModificationCount();
    final int mavenConfigCrc = myProjectsTree.getFilterConfigCrc(fileIndex);
    final int crc = mavenConfigCrc + projectRootModificationCount;

    final File crcFile = new File(mavenConfigFile.getParent(), "configuration.crc");

    if (!force) {
      try {
        DataInputStream crcInput = new DataInputStream(new FileInputStream(crcFile));
        try {
          final int lastCrc = crcInput.readInt();
          if (lastCrc == crc) return; // Project had not change since last config generation.

          LOG.debug(String.format(
            "project configuration changed: lastCrc = %d, currentCrc = %d, projectRootModificationCount = %d, mavenConfigCrc = %d",
            lastCrc, crc, projectRootModificationCount, mavenConfigCrc));
        }
        finally {
          crcInput.close();
        }
      }
      catch (IOException ignored) {
        LOG.debug("Unable to read or find config file: " + ignored.getMessage());
      }
    }

    MavenProjectConfiguration projectConfig = new MavenProjectConfiguration();

    for (MavenProject mavenProject : myMavenProjectsManager.getProjects()) {
      VirtualFile pomXml = mavenProject.getFile();

      Module module = fileIndex.getModuleForFile(pomXml);
      if (module == null) continue;

      if (!Comparing.equal(mavenProject.getDirectoryFile(), fileIndex.getContentRootForFile(pomXml))) continue;

      MavenModuleResourceConfiguration resourceConfig = new MavenModuleResourceConfiguration();

      MavenId projectId = mavenProject.getMavenId();
      resourceConfig.id = new MavenIdBean(projectId.getGroupId(), projectId.getArtifactId(), projectId.getVersion());

      MavenId parentId = mavenProject.getParentId();
      if (parentId != null) {
        resourceConfig.parentId = new MavenIdBean(parentId.getGroupId(), parentId.getArtifactId(), parentId.getVersion());
      }
      resourceConfig.directory = FileUtil.toSystemIndependentName(mavenProject.getDirectory());
      resourceConfig.delimitersPattern = MavenFilteredPropertyPsiReferenceProvider.getDelimitersPattern(mavenProject).pattern();
      for (Map.Entry<String, String> entry : mavenProject.getModelMap().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        if (value != null) {
          resourceConfig.modelMap.put(key, value);
        }
      }

      addEarModelMapEntries(mavenProject, resourceConfig.modelMap);

      Element pluginConfiguration = mavenProject.getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin");

      resourceConfig.outputDirectory = getResourcesPluginGoalOutputDirectory(mavenProject, pluginConfiguration, "resources");
      resourceConfig.testOutputDirectory = getResourcesPluginGoalOutputDirectory(mavenProject, pluginConfiguration, "testResources");

      addResources(resourceConfig.resources, mavenProject.getResources());
      addResources(resourceConfig.testResources, mavenProject.getTestResources());

      addWebResources(module, projectConfig, mavenProject);
      addEjbClientArtifactConfiguration(module, projectConfig, mavenProject);

      resourceConfig.filteringExclusions.addAll(MavenProjectsTree.getFilterExclusions(mavenProject));

      final Properties properties = getFilteringProperties(mavenProject);
      for (Map.Entry<Object, Object> propEntry : properties.entrySet()) {
        resourceConfig.properties.put((String)propEntry.getKey(), (String)propEntry.getValue());
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

    addNonMavenResources(projectConfig);

    final Element element = new Element("maven-project-configuration");
    XmlSerializer.serializeInto(projectConfig, element);
    buildManager.runCommand(() -> {
      buildManager.clearState(myProject);
      FileUtil.createIfDoesntExist(mavenConfigFile);
      try {
        JdomKt.write(element, mavenConfigFile.toPath());

        DataOutputStream crcOutput = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(crcFile)));
        try {
          crcOutput.writeInt(crc);
        }
        finally {
          crcOutput.close();
        }
      }
      catch (IOException e) {
        LOG.debug("Unable to write config file", e);
        throw new RuntimeException(e);
      }
    });
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
                                       @NotNull Module module,
                                       @NotNull MavenModuleResourceConfiguration resourceConfig) {
    if (mavenProject.isAggregator()) return;
    if (Boolean.valueOf(IDEA_MAVEN_DISABLE_MANIFEST)) {
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

      Manifest manifest = new ManifestBuilder(mavenProject).withJdkVersion(jdkVersion).build();
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      try {
        manifest.write(outputStream);
        MavenDomProjectModel domModel = MavenDomUtil.getMavenDomProjectModel(module.getProject(), mavenProject.getFile());
        if(domModel != null) {
          final String resolvedText = MavenPropertyResolver.resolve(outputStream.toString(CharsetToolkit.UTF8), domModel);
          resourceConfig.manifest = Base64.getEncoder().encodeToString(resolvedText.getBytes(StandardCharsets.UTF_8));
        }
      }
      finally {
        StreamUtil.closeStream(outputStream);
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

  private Properties getFilteringProperties(MavenProject mavenProject) {
    final Properties properties = new Properties();

    for (String each : mavenProject.getFilterPropertiesFiles()) {
      try {
        FileInputStream in = new FileInputStream(each);
        try {
          properties.load(in);
        }
        finally {
          in.close();
        }
      }
      catch (IOException ignored) {
      }
    }

    properties.putAll(mavenProject.getProperties());

    properties.put("settings.localRepository", mavenProject.getLocalRepository().getAbsolutePath());

    String jreDir = MavenUtil.getModuleJreHome(myMavenProjectsManager, mavenProject);
    if (jreDir != null) {
      properties.put("java.home", jreDir);
    }

    String javaVersion = MavenUtil.getModuleJavaVersion(myMavenProjectsManager, mavenProject);
    if (javaVersion != null) {
      properties.put("java.version", javaVersion);
    }

    return properties;
  }

  private static void addResources(final List<ResourceRootConfiguration> container, @NotNull Collection<MavenResource> resources) {

    for (MavenResource resource : resources) {
      final String dir = resource.getDirectory();
      if (dir == null) {
        continue;
      }

      final ResourceRootConfiguration props = new ResourceRootConfiguration();
      props.directory = FileUtil.toSystemIndependentName(dir);

      final String target = resource.getTargetPath();
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

  private static void addWebResources(@NotNull Module module, MavenProjectConfiguration projectCfg, MavenProject mavenProject) {
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

    String warSourceDirectory = warCfg.getChildTextTrim("warSourceDirectory");
    if (warSourceDirectory == null) warSourceDirectory = "src/main/webapp";
    if (!FileUtil.isAbsolute(warSourceDirectory)) {
      warSourceDirectory = mavenProject.getDirectory() + '/' + warSourceDirectory;
    }
    artifactResourceCfg.warSourceDirectory = FileUtil.toSystemIndependentName(StringUtil.trimEnd(warSourceDirectory, '/'));

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

        r.directory = directory;
        r.isFiltered = Boolean.parseBoolean(resource.getChildTextTrim("filtering"));

        r.targetPath = resource.getChildTextTrim("targetPath");

        Element includes = resource.getChild("includes");
        if (includes != null) {
          for (Element include : includes.getChildren("include")) {
            String includeText = include.getTextTrim();
            if (!includeText.isEmpty()) {
              r.includes.add(includeText);
            }
          }
          if (includes.getChildren("include").isEmpty()) {
            addSplitAndTrimmed(r.includes, includes.getTextTrim());
          }
        }

        Element excludes = resource.getChild("excludes");
        if (excludes != null) {
          for (Element exclude : excludes.getChildren("exclude")) {
            String excludeText = exclude.getTextTrim();
            if (!excludeText.isEmpty()) {
              r.excludes.add(excludeText);
            }
          }
          if (excludes.getChildren("exclude").isEmpty()) {
            addSplitAndTrimmed(r.excludes, excludes.getTextTrim());
          }
        }

        artifactResourceCfg.webResources.add(r);
      }
    }

    if (filterWebXml) {
      ResourceRootConfiguration r = new ResourceRootConfiguration();
      r.directory = warSourceDirectory;
      r.includes = Collections.singleton("WEB-INF/web.xml");
      r.isFiltered = true;
      r.targetPath = "";
      artifactResourceCfg.webResources.add(r);
    }
  }

  private static void addSplitAndTrimmed(Collection<String> collection, @Nullable String commaSeparatedList) {
    if (commaSeparatedList != null) {
      for (String s : StringUtil.split(commaSeparatedList, ",")) {
        collection.add(s.trim());
      }
    }
  }

  private static void addEjbClientArtifactConfiguration(Module module, MavenProjectConfiguration projectCfg, MavenProject mavenProject) {
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

  private void addNonMavenResources(MavenProjectConfiguration projectCfg) {
    Set<VirtualFile> processedRoots = new HashSet<>();

    for (MavenProject project : myMavenProjectsManager.getProjects()) {
      for (String dir : ContainerUtil.concat(project.getSources(), project.getTestSources())) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(dir);
        if (file != null) {
          processedRoots.add(file);
        }
      }

      for (MavenResource resource : ContainerUtil.concat(project.getResources(), project.getTestResources())) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(resource.getDirectory());
        if (file != null) {
          processedRoots.add(file);
        }
      }
    }

    CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      if (!myMavenProjectsManager.isMavenizedModule(module)) continue;

      for (ContentEntry contentEntry : ModuleRootManager.getInstance(module).getContentEntries()) {
        for (SourceFolder folder : contentEntry.getSourceFolders()) {
          VirtualFile file = folder.getFile();
          if (file == null) continue;

          if (!compilerConfiguration.isExcludedFromCompilation(file) && !isUnderRoots(processedRoots, file)) {
            MavenModuleResourceConfiguration configuration = projectCfg.moduleConfigurations.get(module.getName());
            if (configuration == null) continue;

            List<ResourceRootConfiguration> resourcesList = folder.isTestSource() ? configuration.testResources : configuration.resources;

            final ResourceRootConfiguration cfg = new ResourceRootConfiguration();
            cfg.directory = FileUtil.toSystemIndependentName(file.getPath());

            CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
            if (compilerModuleExtension == null) continue;


            String compilerOutputUrl = folder.isTestSource()
                                       ? compilerModuleExtension.getCompilerOutputUrlForTests()
                                       : compilerModuleExtension.getCompilerOutputUrl();

            cfg.targetPath = VfsUtilCore.urlToPath(compilerOutputUrl);

            convertIdeaExcludesToMavenExcludes(cfg, (CompilerConfigurationImpl)compilerConfiguration);

            resourcesList.add(cfg);
          }
        }
      }
    }
  }

  private static void convertIdeaExcludesToMavenExcludes(ResourceRootConfiguration cfg, CompilerConfigurationImpl compilerConfiguration) {
    for (String pattern : compilerConfiguration.getResourceFilePatterns()) {
      Matcher matcher = SIMPLE_NEGATIVE_PATTERN.matcher(pattern);
      if (matcher.matches()) {
        cfg.excludes.add("**/" + matcher.group(1));
      }
    }
  }

  private static boolean isUnderRoots(Set<VirtualFile> roots, VirtualFile file) {
    for (VirtualFile f = file; f != null; f = f.getParent()) {
      if (roots.contains(file)) {
        return true;
      }
    }

    return false;
  }

}
