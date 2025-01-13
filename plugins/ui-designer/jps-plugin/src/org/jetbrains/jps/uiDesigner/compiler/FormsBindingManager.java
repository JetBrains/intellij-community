// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.uiDesigner.compiler;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.java.CopyResourcesUtil;
import org.jetbrains.jps.incremental.java.FormsParsing;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerConfiguration;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerExtensionService;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class FormsBindingManager extends ModuleLevelBuilder {
  private static final String FORM_EXTENSION = "form";
  private static final FileFilter FORM_SOURCES_FILTER = FileFilters.withExtension(FORM_EXTENSION);
  private static final FileFilter JAVA_SOURCES_FILTER = FileFilters.withExtension("java");
  private static final @NlsSafe String BUILDER_NAME = "form-bindings";
  private static final String JAVA_EXTENSION = ".java";
  private static final Key<Boolean> FORCE_FORMS_REBUILD_FLAG = Key.create("_forms_rebuild_flag_");
  private static final Key<Boolean> FORMS_REBUILD_FORCED = Key.create("_forms_rebuild_forced_flag_");

  public FormsBindingManager() {
    super(BuilderCategory.SOURCE_PROCESSOR);
  }

  @Override
  public void buildStarted(CompileContext context) {
    FORCE_FORMS_REBUILD_FLAG.set(context, Files.exists(getMarkerFile(context)));
  }

  @Override
  public void chunkBuildFinished(CompileContext context, ModuleChunk chunk) {
    FORMS_REBUILD_FORCED.set(context, null); // clear the flag on a per-chunk basis
    super.chunkBuildFinished(context, chunk);
  }

  @Override
  public void buildFinished(CompileContext context) {
    boolean previousValue = FORCE_FORMS_REBUILD_FLAG.get(context, Boolean.FALSE);
    JpsUiDesignerConfiguration config = JpsUiDesignerExtensionService.getInstance().getUiDesignerConfiguration(context.getProjectDescriptor().getProject());
    boolean currentRebuildValue = config != null && !config.isInstrumentClasses();
    if (previousValue == currentRebuildValue) {
      return;
    }

    Path marker = getMarkerFile(context);
    if (currentRebuildValue) {
      FileUtil.createIfDoesntExist(marker.toFile());
    }
    else {
      try {
        Files.deleteIfExists(marker);
      }
      catch (IOException ignored) {
      }
    }
  }

  @Override
  public @NotNull String getPresentableName() {
    return BUILDER_NAME;
  }

  private static @NotNull Path getMarkerFile(CompileContext context) {
    return context.getProjectDescriptor().dataManager.getDataPaths().getDataStorageDir().resolve("forms_rebuild_required");
  }

  @Override
  public @NotNull List<String> getCompilableFileExtensions() {
    return Collections.singletonList(FORM_EXTENSION);
  }

  @Override
  public ExitCode build(CompileContext context, ModuleChunk chunk, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder, OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
    ExitCode exitCode = ExitCode.NOTHING_DONE;
    final JpsProject project = context.getProjectDescriptor().getProject();
    final JpsUiDesignerConfiguration config = JpsUiDesignerExtensionService.getInstance().getOrCreateUiDesignerConfiguration(project);

    if (!config.isInstrumentClasses() && !config.isCopyFormsRuntimeToOutput()) {
      return exitCode;
    }

    final Map<File, ModuleBuildTarget> filesToCompile = FileCollectionFactory.createCanonicalFileMap();
    final Map<File, ModuleBuildTarget> formsToCompile = FileCollectionFactory.createCanonicalFileMap();
    final Map<Path, Collection<Path>> srcToForms = FileCollectionFactory.createCanonicalPathMap();

    if (!JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) && config.isInstrumentClasses() && FORCE_FORMS_REBUILD_FLAG.get(context, Boolean.FALSE)) {
      // force compilation of all forms, but only once per chunk
      if (!FORMS_REBUILD_FORCED.get(context, Boolean.FALSE)) {
        FORMS_REBUILD_FORCED.set(context, Boolean.TRUE);
        FSOperations.markDirty(context, CompilationRound.CURRENT, chunk, FORM_SOURCES_FILTER);
      }
    }

    dirtyFilesHolder.processDirtyFiles(new FileProcessor<>() {
      @Override
      public boolean apply(@NotNull ModuleBuildTarget target, @NotNull File file, @NotNull JavaSourceRootDescriptor descriptor) {
        if (JAVA_SOURCES_FILTER.accept(file)) {
          filesToCompile.put(file, target);
        }
        else if (FORM_SOURCES_FILTER.accept(file)) {
          formsToCompile.put(file, target);
        }
        return true;
      }
    });

    if (config.isInstrumentClasses()) {
      final JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project);
      final JpsCompilerExcludes excludes = configuration.getCompilerExcludes();

      final FSOperations.DirtyFilesHolderBuilder<JavaSourceRootDescriptor, ModuleBuildTarget> holderBuilder = FSOperations.createDirtyFilesHolderBuilder(context, CompilationRound.CURRENT);

      // force compilation of the bound source file if the form is dirty
      final Set<File> alienForms = FileCollectionFactory.createCanonicalFileSet();
      for (final Map.Entry<File, ModuleBuildTarget> entry : formsToCompile.entrySet()) {
        final File form = entry.getKey();
        final ModuleBuildTarget target = entry.getValue();
        try {
          final Collection<Path> sources = findBoundSourceCandidates(context, target, form);
          boolean isFormBound = false;
          for (Path boundSource : sources) {
            if (!excludes.isExcluded(boundSource.toFile())) {
              isFormBound = true;
              FormBindings.addBinding(boundSource, form.toPath(), srcToForms);
              holderBuilder.markDirtyFile(target, boundSource);
              context.getScope().markIndirectlyAffected(target, boundSource);
              filesToCompile.put(boundSource.toFile(), target);
              exitCode = ExitCode.OK;
            }
          }
          if (!isFormBound) {
            context.processMessage(new CompilerMessage(
              getPresentableName(), BuildMessage.Kind.ERROR, FormBundle.message("class.to.bind.not.found"), form.getAbsolutePath()
            ));
          }
        }
        catch (AlienFormFileException e) {
          alienForms.add(form);
        }
      }

      formsToCompile.keySet().removeAll(alienForms);

      // form should be considered dirty if the class it is bound to is dirty
      BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
      for (Map.Entry<File, ModuleBuildTarget> entry : filesToCompile.entrySet()) {
        File srcFile = entry.getKey();
        ModuleBuildTarget target = entry.getValue();
        Collection<Path> boundForms = dataManager.getSourceToFormMap(target).getOutputs(srcFile.toPath());
        if (boundForms == null) {
          continue;
        }

        for (Path formFile : boundForms) {
          if (!excludes.isExcluded(formFile.toFile()) && Files.exists(formFile)) {
            FormBindings.addBinding(srcFile.toPath(), formFile, srcToForms);
            holderBuilder.markDirtyFile(target, formFile);

            context.getScope().markIndirectlyAffected(target, formFile);
            formsToCompile.put(formFile.toFile(), target);
            exitCode = ExitCode.OK;
          }
        }
      }

      BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, holderBuilder.create());
    }

    FormBindings.setFormsToCompile(context, srcToForms);

    if (config.isCopyFormsRuntimeToOutput() && containsValidForm(formsToCompile.keySet())) {
      for (ModuleBuildTarget target : chunk.getTargets()) {
        if (!target.isTests()) {
          final File outputDir = target.getOutputDir();
          if (outputDir != null) {
            final String outputRoot = FileUtilRt.toSystemIndependentName(outputDir.getPath());
            final List<File> generatedFiles = CopyResourcesUtil.copyFormsRuntime(outputRoot, false);
            if (!generatedFiles.isEmpty()) {
              exitCode = ExitCode.OK;
              // now inform others about files just copied
              for (File file : generatedFiles) {
                outputConsumer.registerOutputFile(target, file, Collections.emptyList());
              }
            }
          }
        }
      }
    }

    return exitCode;
  }

  private static boolean containsValidForm(Set<File> files) {
    for (File file : files) {
      try {
        if (FormsParsing.readBoundClassName(file.toPath()) != null) {
          return true;
        }
      }
      catch (IOException | AlienFormFileException ignore) {
      }
    }
    return false;
  }

  private static @NotNull Collection<Path> findBoundSourceCandidates(CompileContext context, final ModuleBuildTarget target, File form) throws IOException, AlienFormFileException {
    final List<JavaSourceRootDescriptor> targetRoots = context.getProjectDescriptor().getBuildRootIndex().getTargetRoots(target, context);
    if (targetRoots.isEmpty()) {
      return List.of();
    }

    String className = FormsParsing.readBoundClassName(form.toPath());
    if (className == null) {
      return List.of();
    }

    for (JavaSourceRootDescriptor rd : targetRoots) {
      final File boundSource = findSourceForClass(rd, className);
      if (boundSource != null) {
        return List.of(boundSource.toPath());
      }
    }

    Set<Path> candidates = FileCollectionFactory.createCanonicalPathSet();
    for (JavaSourceRootDescriptor rd : targetRoots) {
      collectPossibleSourcesForClass(rd, className, candidates);
    }
    return candidates;
  }

  private static @Nullable File findSourceForClass(JavaSourceRootDescriptor rd, final @Nullable String boundClassName) {
    if (boundClassName == null) {
      return null;
    }
    String relPath = suggestRelativePath(rd, boundClassName);
    while (true) {
      final File candidate = new File(rd.getRootFile(), relPath);
      if (candidate.exists()) {
        return candidate.isFile() ? candidate : null;
      }
      final int index = relPath.lastIndexOf('/');
      if (index <= 0) {
        return null;
      }
      relPath = relPath.substring(0, index) + JAVA_EXTENSION;
    }
  }

  private static void collectPossibleSourcesForClass(@NotNull JavaSourceRootDescriptor rootDescriptor,
                                                     @Nullable String boundClassName,
                                                     @NotNull Set<Path> result) {
    if (boundClassName == null) {
      return;
    }

    String relPath = suggestRelativePath(rootDescriptor, boundClassName);
    Path containingDirectory = rootDescriptor.getFile().resolve(relPath).getParent();
    if (containingDirectory == null) {
      return;
    }

    try {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(containingDirectory, "*.java")) {
        for (Path file : stream) {
          if (Files.isRegularFile(file)) {
            result.add(file);
          }
        }
      }
    }
    catch (IOException ignored) {
    }
  }

  private static @NotNull String suggestRelativePath(@NotNull JavaSourceRootDescriptor rd, @NotNull String className) {
    String clsName = className;
    String prefix = rd.getPackagePrefix();
    if (!StringUtil.isEmpty(prefix)) {
      if (!StringUtil.endsWith(prefix, ".")) {
        prefix += ".";
      }
      if (SystemInfo.isFileSystemCaseSensitive? StringUtil.startsWith(clsName, prefix) : StringUtil.startsWithIgnoreCase(clsName, prefix)) {
        clsName = clsName.substring(prefix.length());
      }
    }

    return clsName.replace('.', '/') + JAVA_EXTENSION;
  }
}
