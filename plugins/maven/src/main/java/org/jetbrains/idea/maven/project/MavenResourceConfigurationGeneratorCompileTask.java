// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenResource;
import org.jetbrains.idea.maven.project.compilation.FilteredJarConfigGenerator;
import org.jetbrains.idea.maven.project.compilation.ResourceConfigGenerator;
import org.jetbrains.idea.maven.server.RemotePathTransformerFactory;
import org.jetbrains.jps.maven.model.impl.MavenModuleResourceConfiguration;
import org.jetbrains.jps.maven.model.impl.MavenProjectConfiguration;
import org.jetbrains.jps.maven.model.impl.ResourceRootConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApiStatus.Internal
public final class MavenResourceConfigurationGeneratorCompileTask implements CompileTask {

  private static final Logger LOG = Logger.getInstance(MavenResourceConfigurationGeneratorCompileTask.class);

  private static final Pattern SIMPLE_NEGATIVE_PATTERN = Pattern.compile("!\\?(\\*\\.\\w+)");

  @Override
  public boolean execute(@NotNull CompileContext context) {
    ApplicationManager.getApplication().runReadAction(() -> generateBuildConfiguration(context.isRebuild(), context.getProject()));
    return true;
  }

  private static void generateBuildConfiguration(boolean force, @NotNull Project project) {
    MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);
    if (!mavenProjectsManager.isMavenizedProject()) {
      return;
    }

    final BuildManager buildManager = BuildManager.getInstance();
    File projectSystemIoFile = buildManager.getProjectSystemDirectory(project);

    final Path projectSystemDir = projectSystemIoFile.toPath();
    RemotePathTransformerFactory.Transformer transformer = RemotePathTransformerFactory.createForProject(project);
    final Path mavenConfigFile = projectSystemDir.resolve(MavenProjectConfiguration.CONFIGURATION_FILE_RELATIVE_PATH);

    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
    ProjectFileIndex fileIndex = projectRootManager.getFileIndex();

    final int projectRootModificationCount = (int)projectRootManager.getModificationCount();
    final int mavenConfigCrc = mavenProjectsManager.getFilterConfigCrc(fileIndex);
    final int crc = mavenConfigCrc + projectRootModificationCount;

    final Path crcFile = mavenConfigFile.resolveSibling("configuration.crc");

    if (!force) {
      try (DataInputStream crcInput = new DataInputStream(Files.newInputStream(crcFile, StandardOpenOption.READ))) {
        final int lastCrc = crcInput.readInt();
        if (lastCrc == crc) return; // Project had not changed since last config generation.

        LOG.debug(String.format(
          "project configuration changed: lastCrc = %d, currentCrc = %d, projectRootModificationCount = %d, mavenConfigCrc = %d",
          lastCrc, crc, projectRootModificationCount, mavenConfigCrc));
      }
      catch (IOException e) {
        LOG.debug("Unable to read or find config file: " + e.getMessage());
      }
    }

    MavenProjectConfiguration projectConfig = new MavenProjectConfiguration();
    for (MavenProject mavenProject : mavenProjectsManager.getProjects()) {
      new ResourceConfigGenerator(fileIndex, mavenProjectsManager, transformer, projectConfig, mavenProject).generateResourceConfig();
      new FilteredJarConfigGenerator(fileIndex, mavenProjectsManager, transformer, projectConfig, mavenProject).generateAdditionalJars();
    }
    addNonMavenResources(transformer, projectConfig, mavenProjectsManager, project);

    final Element element = new Element("maven-project-configuration");
    XmlSerializer.serializeInto(projectConfig, element);
    buildManager.runCommand(() -> {
      if (!project.isDefault()) {
        buildManager.clearState(project);
      }
      try {
        JDOMUtil.write(element, mavenConfigFile);
        try (DataOutputStream crcOutput = new DataOutputStream(
          new BufferedOutputStream(Files.newOutputStream(crcFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)))) {
          crcOutput.writeInt(crc);
        }
      }
      catch (IOException e) {
        LOG.debug("Unable to write config file", e);
        throw new RuntimeException(e);
      }
    });
  }

  private static void addNonMavenResources(RemotePathTransformerFactory.Transformer transformer,
                                           @NotNull MavenProjectConfiguration projectCfg,
                                           @NotNull MavenProjectsManager mavenProjectsManager,
                                           @NotNull Project project) {
    Set<VirtualFile> processedRoots = new HashSet<>();

    for (MavenProject mavenProject : mavenProjectsManager.getProjects()) {
      for (String dir : ContainerUtil.concat(mavenProject.getSources(), mavenProject.getTestSources())) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(dir);
        if (file != null) {
          processedRoots.add(file);
        }
      }

      for (MavenResource resource : ContainerUtil.concat(mavenProject.getResources(), mavenProject.getTestResources())) {
        String directory = resource.getDirectory();
        ContainerUtil.addIfNotNull(processedRoots, LocalFileSystem.getInstance().findFileByPath(directory));
      }
    }

    CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(project);

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (!mavenProjectsManager.isMavenizedModule(module)) continue;

      for (ContentEntry contentEntry : ModuleRootManager.getInstance(module).getContentEntries()) {
        for (SourceFolder folder : contentEntry.getSourceFolders()) {
          VirtualFile file = folder.getFile();
          if (file == null) continue;

          if (!compilerConfiguration.isExcludedFromCompilation(file) && !isUnderRoots(processedRoots, file)) {
            MavenModuleResourceConfiguration configuration = projectCfg.moduleConfigurations.get(module.getName());
            if (configuration == null) continue;

            List<ResourceRootConfiguration> resourcesList = folder.isTestSource() ? configuration.testResources : configuration.resources;

            final ResourceRootConfiguration cfg = new ResourceRootConfiguration();
            cfg.directory = transformer.toRemotePathOrSelf(FileUtil.toSystemIndependentName(file.getPath()));

            CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module);
            if (compilerModuleExtension == null) continue;


            String compilerOutputUrl = folder.isTestSource()
                                       ? compilerModuleExtension.getCompilerOutputUrlForTests()
                                       : compilerModuleExtension.getCompilerOutputUrl();

            cfg.targetPath = transformer.toRemotePathOrSelf(VfsUtilCore.urlToPath(compilerOutputUrl));

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
