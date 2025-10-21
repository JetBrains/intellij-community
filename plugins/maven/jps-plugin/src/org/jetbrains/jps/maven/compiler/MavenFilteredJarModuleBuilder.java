// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.compiler;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.FileDeletedEvent;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;
import org.jetbrains.jps.maven.MavenJpsBundle;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.maven.model.impl.MavenFilteredJarConfiguration;
import org.jetbrains.jps.maven.model.impl.MavenProjectConfiguration;
import org.jetbrains.jps.maven.model.impl.MavenResourcesTarget;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static com.intellij.util.containers.ContainerUtil.filter;

public final class MavenFilteredJarModuleBuilder extends ModuleLevelBuilder {
  private static final Key<MavenProjectConfiguration> CACHE_JAR_CONFIG =
    Key.create("MavenFilteredJarModuleBuilder.MavenProjectConfiguration.cache");
  private final ConcurrentMap<String, BuildListener> listenerMap = new ConcurrentHashMap<>();

  MavenFilteredJarModuleBuilder() {
    super(BuilderCategory.CLASS_INSTRUMENTER);
  }

  @Override
  public void chunkBuildStarted(CompileContext context, ModuleChunk chunk) {
    List<MavenFilteredJarConfiguration> jarsConfig = getJarsConfig(context, chunk);
    if (jarsConfig.isEmpty()) {
      return;
    }
    BuildListener listener = new BuildListener() {
      @Override
      public void filesGenerated(@NotNull FileGeneratedEvent event) {
        for (Pair<String, String> pair : event.getPaths()) {
          var configs = filter(jarsConfig, c -> c.originalOutput.equals(FileUtilRt.toSystemDependentName(pair.first)));
          configs.forEach(config -> {
            copyCreatedFileIfNeeded(pair, config, context);
          });
        }
      }

      @Override
      public void filesDeleted(@NotNull FileDeletedEvent event) {
        List<MavenFilteredJarConfiguration> jarsConfig = getJarsConfig(context, chunk);
        if (jarsConfig.isEmpty()) return;
        for (String deletedFilePath : event.getFilePaths()) {
          var configs = filter(jarsConfig, c -> FileUtil.isAncestor(c.originalOutput, deletedFilePath, true));
          for (MavenFilteredJarConfiguration config : configs) {
            deleteFile(config, deletedFilePath);
          }
        }
      }
    };
    listenerMap.put(nameFor(chunk), listener);
    context.addBuildListener(listener);
  }

  private static String nameFor(ModuleChunk chunk) {
    return chunk.getTargets().stream().map(t -> t.getPresentableName()).collect(Collectors.joining());
  }

  private static void deleteFile(MavenFilteredJarConfiguration config, String path) {
    String relative = FileUtilRt.getRelativePath(new File(config.originalOutput), new File(path));
    if (relative == null) {
      return;
    }
    try {
      FileUtilRt.deleteRecursively(new File(new File(config.jarOutput), relative).toPath());
    }
    catch (IOException ignored) {
    }
  }

  private static void copyCreatedFileIfNeeded(Pair<String, String> pair, MavenFilteredJarConfiguration config, CompileContext context) {
    var from = new File(new File(pair.first), pair.second);
    var to = new File(new File(config.jarOutput), pair.second);
    var filter = new MavenPatternFileFilter(config.includes, config.excludes);
    if (filter.accept(pair.second)) {
      try {
        FSOperations.copy(from, to);
      }
      catch (IOException e) {
        context.processMessage(
          new CompilerMessage(MavenJpsBundle.message("maven.filter.jar.compiler"), BuildMessage.Kind.ERROR, e.getMessage()));
      }
    }
  }

  public static @NotNull List<MavenFilteredJarConfiguration> getJarsConfig(CompileContext context, ModuleChunk chunk) {
    return chunk.getTargets().stream()
      .flatMap(target -> getJarConfigurations(context, target.getModule().getName(), target.isTests()).stream()).collect(
      Collectors.toList());
  }

  static List<MavenFilteredJarConfiguration> getJarsConfig(@NotNull CompileContext context, @NotNull MavenResourcesTarget target) {
    return getJarConfigurations(context, target.getModule().getName(), target.isTests());
  }

  @Override
  public void chunkBuildFinished(CompileContext context, ModuleChunk chunk) {
    BuildListener buildListener = listenerMap.remove(nameFor(chunk));
    if (buildListener != null) {
      context.removeBuildListener(buildListener);
    }
  }

  @Override
  public ExitCode build(CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer) throws ProjectBuildException, IOException {

    return ExitCode.OK;
  }

  private static @NotNull List<MavenFilteredJarConfiguration> getJarConfigurations(CompileContext context,
                                                                                   String moduleName,
                                                                                   boolean isTests) {
    MavenProjectConfiguration projectConfig = getOrLoadConfiguration(context);
    if (projectConfig == null) return Collections.emptyList();
    return filter(projectConfig.jarsConfiguration.values(),
                  it -> it.moduleName.equals(moduleName) && it.isTest == isTests);
  }

  private static @Nullable MavenProjectConfiguration getOrLoadConfiguration(CompileContext context) {
    MavenProjectConfiguration projectConfig = context.getUserData(CACHE_JAR_CONFIG);
    if (projectConfig != null) return projectConfig;
    BuildDataPaths dataPaths = context.getProjectDescriptor().dataManager.getDataPaths();
    projectConfig = JpsMavenExtensionService.getInstance().getMavenProjectConfiguration(dataPaths);
    context.putUserData(CACHE_JAR_CONFIG, projectConfig);
    return projectConfig;
  }


  @Override
  public @NotNull List<String> getCompilableFileExtensions() {
    return List.of();
  }

  @Override
  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getPresentableName() {
    return MavenJpsBundle.message("maven.filter.jar.compiler");
  }
}
