// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.compiler;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.maven.MavenJpsBundle;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.maven.model.impl.*;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class MavenResourcesBuilder extends TargetBuilder<MavenResourceRootDescriptor, MavenResourcesTarget> {
  private static final Logger LOG = Logger.getInstance(MavenResourcesBuilder.class);

  public MavenResourcesBuilder() {
    super(Arrays.asList(MavenResourcesTargetType.PRODUCTION, MavenResourcesTargetType.TEST));
  }

  @Override
  public void build(final @NotNull MavenResourcesTarget target, final @NotNull DirtyFilesHolder<MavenResourceRootDescriptor, MavenResourcesTarget> holder, final @NotNull BuildOutputConsumer outputConsumer, final @NotNull CompileContext context) throws ProjectBuildException, IOException {
    final BuildDataPaths dataPaths = context.getProjectDescriptor().dataManager.getDataPaths();
    final MavenProjectConfiguration projectConfig = JpsMavenExtensionService.getInstance().getMavenProjectConfiguration(dataPaths);
    if (projectConfig == null) {
      context.processMessage(new CompilerMessage(MavenJpsBundle.message("maven.resources.compiler"), BuildMessage.Kind.ERROR,
                                                 MavenJpsBundle.message("maven.project.configuration.required", target.getModule().getName())));
      throw new StopBuildException();
    }

    final MavenModuleResourceConfiguration config = target.getModuleResourcesConfiguration(dataPaths);
    if (config == null) {
      return;
    }

    final List<MavenFilteredJarConfiguration> jarConfigurations = MavenFilteredJarModuleBuilder.getJarsConfig(context, target);

    final Map<MavenResourceRootDescriptor, List<File>> files = new HashMap<>();

    holder.processDirtyFiles(new FileProcessor<MavenResourceRootDescriptor, MavenResourcesTarget>() {

      @Override
      public boolean apply(@NotNull MavenResourcesTarget t, @NotNull File file, @NotNull MavenResourceRootDescriptor rd) throws IOException {
        assert target == t;

        List<File> fileList = files.get(rd);
        if (fileList == null) {
          fileList = new ArrayList<>();
          files.put(rd, fileList);
        }

        fileList.add(file);
        return true;
      }
    });

    MavenResourceRootDescriptor[] roots = files.keySet().toArray(new MavenResourceRootDescriptor[0]);
    Arrays.sort(roots, (r1, r2) -> {
      int res = r1.getIndexInPom() - r2.getIndexInPom();

      if (r1.isOverwrite()) {
        assert r2.isOverwrite(); // 'overwrite' parameters is common for all roots in module.

        return res;
      }

      if (r1.getConfiguration().isFiltered && !r2.getConfiguration().isFiltered) return 1;
      if (!r1.getConfiguration().isFiltered && r2.getConfiguration().isFiltered) return -1;

      if (!r1.getConfiguration().isFiltered) {
        res = -res;
      }

      return res;
    });

    MavenResourceFileProcessor fileProcessor = new MavenResourceFileProcessor(projectConfig, target.getModule().getProject(), config);

    context.processMessage(new ProgressMessage(MavenJpsBundle.message("copying.resources", target.getModule().getName())));
    for (MavenResourceRootDescriptor rd : roots) {
      for (File file : files.get(rd)) {

        String relPath = FileUtil.getRelativePath(rd.getRootFile(), file);
        if (relPath == null) {
          continue;
        }
        final String outputDirectory = target.isTests() ? config.testOutputDirectory : config.outputDirectory;
        final File outputDir = MavenResourcesTarget.getOutputDir(target.getModuleOutputDir(), rd.getConfiguration(), outputDirectory);
        if (outputDir == null) {
          continue;
        }
        File outputFile = new File(outputDir, relPath);
        String sourcePath = file.getPath();

        try {
          fileProcessor.copyFile(file, outputFile, rd.getConfiguration(), context, FileFilters.EVERYTHING);
          outputConsumer.registerOutputFile(outputFile, Collections.singleton(sourcePath));
          copyToAdditionalJars(jarConfigurations, outputDir, relPath, context, outputConsumer);
        }
        catch (UnsupportedEncodingException e) {
          context.processMessage(
            new CompilerMessage(MavenJpsBundle.message("maven.resources.compiler"), BuildMessage.Kind.INFO,
                                MavenJpsBundle.message("resource.was.not.copied", e.getMessage()), sourcePath));
        }
        catch (IOException e) {
          context.processMessage(new CompilerMessage(MavenJpsBundle.message("maven.resources.compiler"), BuildMessage.Kind.ERROR,
                                                     MavenJpsBundle.message("failed.to.copy.0.to.1.2", sourcePath, outputFile.getAbsolutePath(), e.getMessage())));
          LOG.info(e);
        }

        if (context.isCanceled()) {
          return;
        }
      }
    }

    context.checkCanceled();

    context.processMessage(new ProgressMessage(""));
  }

  private void copyToAdditionalJars(List<MavenFilteredJarConfiguration> configurations,
                                    File outputDir,
                                    String relPath,
                                    @NotNull CompileContext context,
                                    @NotNull BuildOutputConsumer consumer) throws IOException {
    if (configurations.isEmpty()) return;
    var applicableConfigurations = ContainerUtil.filter(configurations,
                                                        it -> FileUtil.filesEqual(outputDir, new File(it.originalOutput)));
    for (MavenFilteredJarConfiguration config : applicableConfigurations) {
      var filter = new MavenPatternFileFilter(config.includes, config.excludes);
      if (filter.accept(relPath)) {
        var from = new File(outputDir, relPath);
        var to = new File(new File(config.jarOutput), relPath);
        FSOperations.copy(from, to);
      }
    }
  }

  @Override
  public @NotNull String getPresentableName() {
    return MavenJpsBundle.message("maven.resources.compiler");
  }
}
