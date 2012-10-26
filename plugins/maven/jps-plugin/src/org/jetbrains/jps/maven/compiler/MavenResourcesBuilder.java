package org.jetbrains.jps.maven.compiler;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.maven.model.impl.MavenModuleResourceConfiguration;
import org.jetbrains.jps.maven.model.impl.MavenResourceRootDescriptor;
import org.jetbrains.jps.maven.model.impl.MavenResourcesTarget;
import org.jetbrains.jps.maven.model.impl.MavenResourcesTargetType;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/6/11
 */
public class MavenResourcesBuilder extends TargetBuilder<MavenResourceRootDescriptor, MavenResourcesTarget> {
  public static final String BUILDER_NAME = "maven-resources";

  public MavenResourcesBuilder() {
    super(Arrays.asList(MavenResourcesTargetType.PRODUCTION, MavenResourcesTargetType.TEST));
  }

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  @Override
  public void build(@NotNull MavenResourcesTarget target, @NotNull final DirtyFilesHolder<MavenResourceRootDescriptor, MavenResourcesTarget> holder, @NotNull final BuildOutputConsumer outputConsumer, @NotNull final CompileContext context) throws ProjectBuildException, IOException {
    final MavenModuleResourceConfiguration config = target.getModuleResourcesConfiguration(
      context.getProjectDescriptor().dataManager.getDataPaths());
    if (config == null) {
      return;
    }
    final Set<String> filteringExcludedExtensions = config.getFiltetingExcludedExtensions();
    holder.processDirtyFiles(new FileProcessor<MavenResourceRootDescriptor, MavenResourcesTarget>() {
      @Override
      public boolean apply(MavenResourcesTarget target, File file, MavenResourceRootDescriptor rd) throws IOException {
        final String relPath = FileUtil.getRelativePath(rd.getRootFile(), file);
        if (relPath == null) {
          return true;
        }
        final String sourcePath = file.getPath();
        if (rd.isIncluded(relPath)) {
          final File outputDir = MavenResourcesTarget.getOutputDir(target.getModuleOutputDir(), rd.getConfiguration());
          if (outputDir != null) {
            final File outputFile = new File(outputDir, relPath);
            final boolean shouldFilter = rd.getConfiguration().isFiltered && !filteringExcludedExtensions.contains(getExtension(file));
            // todo: support filtering
            FileUtil.copyContent(file, outputFile);
            outputConsumer.registerOutputFile(outputFile.getPath(), Collections.singleton(sourcePath));
          }
        }
        return true;
      }
    });
  }

  private static String getExtension(File file) {
    final String name = file.getName();
    final int dotindex = name.lastIndexOf(".");
    if (dotindex < 0) {
      return "";
    }
    return name.substring(dotindex + 1);
  }

  public String getDescription() {
    return "Maven Resource Builder";
  }

}
