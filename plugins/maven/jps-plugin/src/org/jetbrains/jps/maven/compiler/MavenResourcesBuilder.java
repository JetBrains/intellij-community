package org.jetbrains.jps.maven.compiler;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.maven.model.impl.*;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/6/11
 */
public class MavenResourcesBuilder extends TargetBuilder<MavenResourceRootDescriptor, MavenResourcesTarget> {
  public static final String BUILDER_NAME = "Maven Resources Compiler";

  public MavenResourcesBuilder() {
    super(Arrays.asList(MavenResourcesTargetType.PRODUCTION, MavenResourcesTargetType.TEST));
  }

  @Override
  public void build(@NotNull MavenResourcesTarget target, @NotNull final DirtyFilesHolder<MavenResourceRootDescriptor, MavenResourcesTarget> holder, @NotNull final BuildOutputConsumer outputConsumer, @NotNull final CompileContext context) throws ProjectBuildException, IOException {
    final BuildDataPaths dataPaths = context.getProjectDescriptor().dataManager.getDataPaths();
    final MavenProjectConfiguration projectConfig = JpsMavenExtensionService.getInstance().getMavenProjectConfiguration(dataPaths);
    final MavenModuleResourceConfiguration config = target.getModuleResourcesConfiguration(dataPaths);
    if (config == null) {
      return;
    }

    final MavenResourceFileProcessor fileProcessor = new MavenResourceFileProcessor(projectConfig, target.getModule().getProject(), config);
    holder.processDirtyFiles(new FileProcessor<MavenResourceRootDescriptor, MavenResourcesTarget>() {

      @Override
      public boolean apply(MavenResourcesTarget target, File file, MavenResourceRootDescriptor rd) throws IOException {
        final String relPath = FileUtil.getRelativePath(rd.getRootFile(), file);
        if (relPath == null) {
          return true;
        }
        final File outputDir = MavenResourcesTarget.getOutputDir(target.getModuleOutputDir(), rd.getConfiguration());
        if (outputDir == null) {
          return true;
        }
        final File outputFile = new File(outputDir, relPath);
        final String sourcePath = file.getPath();
        try {
          context.processMessage(new ProgressMessage("Copying resources... [" + target.getModule().getName() + "]"));

          fileProcessor.copyFile(file, outputFile, rd.getConfiguration(), context, FileUtilRt.ALL_FILES);
          outputConsumer.registerOutputFile(outputFile, Collections.singleton(sourcePath));
        }
        catch (UnsupportedEncodingException e) {
          context.processMessage(
            new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.INFO, "Resource was not copied: " + e.getMessage(), sourcePath));
        }
        return !context.getCancelStatus().isCanceled();
      }
    });

    context.checkCanceled();

    context.processMessage(new ProgressMessage(""));
  }


  @NotNull
  public String getPresentableName() {
    return BUILDER_NAME;
  }
}
