/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.uiDesigner.compiler;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
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
import org.jetbrains.jps.incremental.storage.OneToManyPathsMapping;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerConfiguration;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerExtensionService;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class FormsBindingManager extends FormsBuilder {
  private static final String JAVA_EXTENSION = ".java";
  private static final Key<Boolean> FORCE_FORMS_REBUILD_FLAG = Key.create("_forms_rebuild_flag_");
  private static final Key<Boolean> FORMS_REBUILD_FORCED = Key.create("_forms_rebuild_forced_flag_");
  public FormsBindingManager() {
    super(BuilderCategory.SOURCE_PROCESSOR, "form-bindings");
  }

  @Override
  public void buildStarted(CompileContext context) {
    FORCE_FORMS_REBUILD_FLAG.set(context, getMarkerFile(context).exists());
  }

  @Override
  public void chunkBuildFinished(CompileContext context, ModuleChunk chunk) {
    FORMS_REBUILD_FORCED.set(context, null); // clear the flag on per-chunk basis
    super.chunkBuildFinished(context, chunk);
  }

  @Override
  public void buildFinished(CompileContext context) {
    final boolean previousValue = FORCE_FORMS_REBUILD_FLAG.get(context, Boolean.FALSE);
    final JpsUiDesignerConfiguration config = JpsUiDesignerExtensionService.getInstance().getUiDesignerConfiguration(context.getProjectDescriptor().getProject());
    final boolean currentRebuildValue = config != null && !config.isInstrumentClasses();
    if (previousValue != currentRebuildValue) {
      final File marker = getMarkerFile(context);
      if (currentRebuildValue) {
        FileUtil.createIfDoesntExist(marker);
      }
      else {
        FileUtil.delete(marker);
      }
    }
  }

  @NotNull
  private static File getMarkerFile(CompileContext context) {
    return new File(context.getProjectDescriptor().dataManager.getDataPaths().getDataStorageRoot(), "forms_rebuild_required");
  }

  @Override
  public List<String> getCompilableFileExtensions() {
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

    final Map<File, ModuleBuildTarget> filesToCompile = new THashMap<>(FileUtil.FILE_HASHING_STRATEGY);
    final Map<File, ModuleBuildTarget> formsToCompile = new THashMap<>(FileUtil.FILE_HASHING_STRATEGY);
    final Map<File, Collection<File>> srcToForms = new THashMap<>(FileUtil.FILE_HASHING_STRATEGY);

    if (!JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) && config.isInstrumentClasses() && FORCE_FORMS_REBUILD_FLAG.get(context, Boolean.FALSE)) {
      // force compilation of all forms, but only once per chunk
      if (!FORMS_REBUILD_FORCED.get(context, Boolean.FALSE)) {
        FORMS_REBUILD_FORCED.set(context, Boolean.TRUE);
        FSOperations.markDirty(context, CompilationRound.CURRENT, chunk, FORM_SOURCES_FILTER);
      }
    }

    dirtyFilesHolder.processDirtyFiles(new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
      public boolean apply(ModuleBuildTarget target, File file, JavaSourceRootDescriptor descriptor) throws IOException {
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
      final JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
      final JpsCompilerExcludes excludes = configuration.getCompilerExcludes();

      // force compilation of bound source file if the form is dirty
      for (final Map.Entry<File, ModuleBuildTarget> entry : formsToCompile.entrySet()) {
        final File form = entry.getKey();
        final ModuleBuildTarget target = entry.getValue();
        final Collection<File> sources = findBoundSourceCandidates(context, target, form);
        for (File boundSource : sources) {
          if (!excludes.isExcluded(boundSource)) {
            addBinding(boundSource, form, srcToForms);
            FSOperations.markDirty(context, CompilationRound.CURRENT, boundSource);
            filesToCompile.put(boundSource, target);
            exitCode = ExitCode.OK;
          }
        }
      }

      // form should be considered dirty if the class it is bound to is dirty
      final OneToManyPathsMapping sourceToFormMap = context.getProjectDescriptor().dataManager.getSourceToFormMap();
      for (Map.Entry<File, ModuleBuildTarget> entry : filesToCompile.entrySet()) {
        final File srcFile = entry.getKey();
        final ModuleBuildTarget target = entry.getValue();
        final Collection<String> boundForms = sourceToFormMap.getState(srcFile.getPath());
        if (boundForms != null) {
          for (String formPath : boundForms) {
            final File formFile = new File(formPath);
            if (!excludes.isExcluded(formFile) && formFile.exists()) {
              addBinding(srcFile, formFile, srcToForms);
              FSOperations.markDirty(context, CompilationRound.CURRENT, formFile);
              formsToCompile.put(formFile, target);
              exitCode = ExitCode.OK;
            }
          }
        }
      }
    }

    FORMS_TO_COMPILE.set(context, srcToForms.isEmpty()? null : srcToForms);

    if (config.isCopyFormsRuntimeToOutput() && containsValidForm(formsToCompile.keySet())) {
      for (ModuleBuildTarget target : chunk.getTargets()) {
        if (!target.isTests()) {
          final File outputDir = target.getOutputDir();
          if (outputDir != null) {
            final String outputRoot = FileUtil.toSystemIndependentName(outputDir.getPath());
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
        if (FormsParsing.readBoundClassName(file) != null) {
          return true;
        }
      }
      catch (IOException ignore) {
      }
    }
    return false;
  }

  @NotNull
  private static Collection<File> findBoundSourceCandidates(CompileContext context, final ModuleBuildTarget target, File form) throws IOException {
    final List<JavaSourceRootDescriptor> targetRoots = context.getProjectDescriptor().getBuildRootIndex().getTargetRoots(target, context);
    if (targetRoots.isEmpty()) {
      return Collections.emptyList();
    }
    final String className = FormsParsing.readBoundClassName(form);
    if (className == null) {
      return Collections.emptyList();
    }
    for (JavaSourceRootDescriptor rd : targetRoots) {
      final File boundSource = findSourceForClass(rd, className);
      if (boundSource != null) {
        return Collections.singleton(boundSource);
      }
    }

    final Set<File> candidates = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
    for (JavaSourceRootDescriptor rd : targetRoots) {
      candidates.addAll(findPossibleSourcesForClass(rd, className));
    }
    return candidates;
  }

  @Nullable
  private static File findSourceForClass(JavaSourceRootDescriptor rd, final @Nullable String boundClassName) throws IOException {
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

  @NotNull
  private static Collection<File> findPossibleSourcesForClass(JavaSourceRootDescriptor rd, final @Nullable String boundClassName) throws IOException {
    if (boundClassName == null) {
      return Collections.emptyList();
    }
    String relPath = suggestRelativePath(rd, boundClassName);
    final File containingDirectory = new File(rd.getRootFile(), relPath).getParentFile();
    if (containingDirectory == null) {
      return Collections.emptyList();
    }
    final File[] files = containingDirectory.listFiles(FileFilters.withExtension("java"));
    if (files == null || files.length == 0) {
      return Collections.emptyList();
    }
    return Arrays.asList(files); 
  }

  @NotNull
  private static String suggestRelativePath(@NotNull JavaSourceRootDescriptor rd, @NotNull String className) {
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
