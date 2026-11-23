// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.skeletons;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.python.sdk.backend.PythonEnvironmentContext;
import com.intellij.python.sdk.backend.PythonEnvironmentFacts;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.skeleton.PySkeletonHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.intellij.openapi.util.NlsContexts.ProgressDetails;
import static com.intellij.openapi.util.NlsContexts.ProgressText;

/**
 * Handles a refresh of the skeletons of one environment, see {@link PythonEnvironmentContext}.
 * Does all the heavy lifting calling skeleton generator, managing blacklists, etc.
 * One-time, non-reusable instances.
 * <br/>
 */
public final class PySkeletonRefresher {
  private static final Logger LOG = Logger.getInstance(PySkeletonRefresher.class);

  private static int ourGeneratingCount = 0;

  private final @Nullable Project myProject;
  private final @Nullable ProgressIndicator myIndicator;
  private final @NotNull PythonEnvironmentContext myContext;
  private final @NotNull PythonEnvironmentFacts myFacts;
  private Path mySkeletonsPath;
  private List<String> myExtraSyspath;
  private final PySkeletonGenerator mySkeletonsGenerator;

  public static synchronized boolean isGeneratingSkeletons() {
    return ourGeneratingCount > 0;
  }

  private static synchronized void changeGeneratingSkeletons(int increment) {
    ourGeneratingCount += increment;
  }

  @RequiresBackgroundThread(generateAssertion = false)
  public @NotNull List<String> regenerateSkeletons() throws InvalidSdkException, ExecutionException {
    final Path skeletonsPath = getSkeletonsPath();
    try {
      Files.createDirectories(skeletonsPath);
    }
    catch (IOException e) {
      throw new InvalidSdkException(PyBundle.message("sdk.gen.cannot.create.skeleton.dir", myContext.getKey().getPath()), e);
    }

    indicate(PyBundle.message("sdk.gen.launching.generator"));
    final List<PySkeletonGenerator.GenerationResult> results = updateOrCreateSkeletons();
    final List<String> failedModules = ContainerUtil.mapNotNull(results, result -> {
      if (result.getGenerationStatus() == PySkeletonGenerator.GenerationStatus.FAILED) {
        return result.getModuleName();
      }
      return null;
    });

    indicate(PyBundle.message("sdk.gen.reloading"));
    mySkeletonsGenerator.refreshGeneratedSkeletons();

    indicate(PyBundle.message("sdk.gen.cleaning.up"));
    cleanUpSkeletons(skeletonsPath);

    if (myProject != null) {
      ApplicationManager.getApplication()
        .invokeLater(() -> DaemonCodeAnalyzer.getInstance(myProject).restart(this), myProject.getDisposed());
    }

    return failedModules;
  }

  /**
   * Creates a new object that refreshes the skeletons of the environment of {@code context}.
   *
   * @param project       the project whose daemon restarts after the refresh, or null for none
   * @param facts         the version and the {@code sys.path} of the environment
   * @param skeletonsPath if known; null means the directory of the environment, see {@link PythonEnvironmentContext#getSkeletonsDirectory()}
   * @param indicator     to report progress of long operations
   * @param folder        the directory where the generator searches for binary modules, or null
   */
  public PySkeletonRefresher(@Nullable Project project,
                             @NotNull PythonEnvironmentContext context,
                             @NotNull PythonEnvironmentFacts facts,
                             @Nullable Path skeletonsPath,
                             @Nullable ProgressIndicator indicator,
                             @Nullable String folder)
    throws InvalidSdkException {
    myProject = project;
    myIndicator = indicator;
    myContext = context;
    myFacts = facts;
    mySkeletonsPath = skeletonsPath;
    mySkeletonsGenerator = new PyTargetsSkeletonGenerator(getSkeletonsPath(), myContext, folder);
  }

  /**
   * Creates the skeletons directory of the environment if needed.
   *
   * @return the skeletons directory, guaranteed to be already created.
   */
  public @NotNull Path getSkeletonsPath() throws InvalidSdkException {
    if (mySkeletonsPath == null) {
      final Path directory = myContext.getSkeletonsDirectory();
      if (directory == null) {
        throw new InvalidSdkException(PyBundle.message("sdk.gen.cannot.create.skeleton.dir", myContext.getKey().getPath()));
      }
      try {
        mySkeletonsPath = Files.createDirectories(directory);
      }
      catch (IOException e) {
        throw new InvalidSdkException(PyBundle.message("sdk.gen.cannot.create.skeleton.dir", myContext.getKey().getPath()), e);
      }
    }
    return Objects.requireNonNull(mySkeletonsPath);
  }

  private void indicate(@ProgressText String msg) {
    LOG.debug("Progress message: " + msg);
    if (myIndicator != null) {
      myIndicator.checkCanceled();
      myIndicator.setText(msg);
      myIndicator.setText2("");
    }
  }

  private void indicateMinor(@ProgressDetails String msg) {
    LOG.debug("Progress message (minor): " + msg);
    if (myIndicator != null) {
      myIndicator.setText2(msg);
    }
  }

  /**
   * The local paths of {@code sys.path} of the environment that exist, without the skeletons and the local copies of the
   * remote sources of a target.
   */
  private static List<String> calculateExtraSysPath(final @NotNull PythonEnvironmentFacts facts,
                                                    final @Nullable Path skeletonsPath,
                                                    final @Nullable Path remoteSourcesDirectory) {
    final List<String> sysPath = facts.getSysPath();
    if (sysPath == null) return List.of();
    return ContainerUtil.filter(sysPath, path -> {
      // We compare normalized paths, not raw strings, because "c:/some/folder" equals "c:\\some\\bin\\..\\folder\\"
      final Path normalized = Path.of(path).normalize();
      return Files.exists(normalized) &&
             (skeletonsPath == null || !normalized.equals(skeletonsPath.normalize())) &&
             (remoteSourcesDirectory == null || !normalized.startsWith(remoteSourcesDirectory.normalize()));
    });
  }

  @RequiresBackgroundThread(generateAssertion = false)
  private @NotNull List<PySkeletonGenerator.GenerationResult> updateOrCreateSkeletons() throws InvalidSdkException, ExecutionException {
    final long startTime = System.currentTimeMillis();
    final PySkeletonGenerator.Builder builder = mySkeletonsGenerator
      .commandBuilder()
      .extraSysPath(getExtraSyspath());
    final List<String> stdlibStubs = PyTypeShed.INSTANCE.findStdlibModuleNames(getLanguageLevel());
    if (!stdlibStubs.isEmpty()) {
      builder.extraArgs("--stdlib-stubs", String.join(",", stdlibStubs));
    }
    final List<PySkeletonGenerator.GenerationResult> result = builder.runGeneration(myIndicator);
    LOG.info("Rebuilding skeletons for binaries took " + (System.currentTimeMillis() - startTime) + " ms");
    return result;
  }

  /**
   * Generates the skeletons of the environment of {@code context} in its skeletons directory, see
   * {@link PythonEnvironmentContext#getSkeletonsDirectory()}.
   *
   * @param project the project whose daemon restarts after the refresh, or null for none
   */
  @RequiresBackgroundThread(generateAssertion = false)
  public static void refreshSkeletonsOfEnvironment(@Nullable Project project,
                                                   @NotNull PythonEnvironmentContext context,
                                                   @NotNull PythonEnvironmentFacts facts)
    throws InvalidSdkException, ExecutionException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (context.getSkeletonsDirectory() == null) {
      LOG.info("Could not find skeletons path for " + context.getKey().getPath());
      return;
    }
    // An update runs one generation for each environment at a time, see PythonEnvironments.
    LOG.info("Refreshing skeletons for " + context.getKey().getPath());
    final PySkeletonRefresher refresher = new PySkeletonRefresher(project, context, facts, null, indicator, null);

    changeGeneratingSkeletons(1);
    try {
      final List<String> errors = refresher.regenerateSkeletons();
      if (!errors.isEmpty()) {
        var failedSkeletons = StringUtil.join(errors, ", ");
        LOG.warn(String.format("%s: %s", PyBundle.message("sdk.some.skeletons.failed"), failedSkeletons));
      }
    }
    finally {
      changeGeneratingSkeletons(-1);
    }
  }

  /**
   * For every existing skeleton file, take its module file name,
   * and remove the skeleton if the module file does not exist.
   * Works recursively starting from dir. Removes dirs that become empty.
   */
  private void cleanUpSkeletons(final Path dir) {
    indicateMinor(dir.toString());
    final List<Path> files = listChildren(dir);
    if (files == null) {
      return;
    }
    for (Path item : files) {
      if (Files.isDirectory(item)) {
        cleanUpSkeletons(item);
        // was the dir emptied?
        final List<Path> remaining = listChildren(item);
        if (remaining != null && remaining.isEmpty()) {
          deleteOrLog(item);
        }
        else if (remaining != null && remaining.size() == 1) { //clean also if contains only __init__.py
          final Path lastFile = remaining.getFirst();
          if (PyNames.INIT_DOT_PY.equals(lastFile.getFileName().toString()) && isEmptyFile(lastFile)) {
            boolean deleted = deleteOrLog(lastFile);
            if (deleted) deleteOrLog(item);
          }
        }
      }
      else if (Files.isRegularFile(item)) {
        // clean up an individual file
        final String itemName = item.getFileName().toString();
        if (PyNames.INIT_DOT_PY.equals(itemName) && isEmptyFile(item)) continue; // these are versionless
        if (PySkeletonGenerator.BLACKLIST_FILE_NAME.equals(itemName)) continue; // don't touch the blacklist
        if (PySkeletonGenerator.STATE_MARKER_FILE.equals(itemName)) continue;
        if (PyBuiltinCache.getBuiltinsFileName(getLanguageLevel()).equals(itemName)) {
          continue;
        }
        final PySkeletonHeader header = PySkeletonHeader.readSkeletonHeader(item);
        String binaryFile = null;
        boolean canLive = header != null;
        if (canLive) {
          binaryFile = header.getBinaryFile();
          canLive = PySkeletonHeader.BUILTIN_NAME.equals(binaryFile) ||
                    mySkeletonsGenerator.exists(binaryFile);
        }
        if (!canLive) {
          LOG.debug("Cleaning up skeleton " + item + (binaryFile != null ? " for non existing binary " + binaryFile : ""));
          deleteOrLog(item);
        }
      }
    }
  }

  private static boolean deleteOrLog(@NotNull Path item) {
    try {
      boolean deleted = Files.deleteIfExists(item);
      if (!deleted) LOG.warn("Failed to delete skeleton file " + item);
      return deleted;
    }
    catch (IOException e) {
      LOG.warn("Failed to delete skeleton file " + item, e);
      return false;
    }
  }

  private static @Nullable List<Path> listChildren(@NotNull Path dir) {
    try (Stream<Path> children = Files.list(dir)) {
      return children.toList();
    }
    catch (IOException e) {
      LOG.warn("Failed to list skeleton directory " + dir, e);
      return null;
    }
  }

  private static boolean isEmptyFile(@NotNull Path file) {
    try {
      return Files.size(file) == 0;
    }
    catch (IOException e) {
      LOG.warn("Failed to read size of skeleton file " + file, e);
      return false;
    }
  }

  private List<String> getExtraSyspath() {
    if (myExtraSyspath == null) {
      myExtraSyspath = calculateExtraSysPath(myFacts, mySkeletonsPath, myContext.getRemoteSourcesDirectory());
    }
    return myExtraSyspath;
  }

  /** The language level of the version of the environment, or the default one when no version is known. */
  private @NotNull LanguageLevel getLanguageLevel() {
    final String version = myFacts.getVersion();
    final LanguageLevel level = version != null ? LanguageLevel.getLanguageLevelFromVersionStringStaticSafe(version) : null;
    return level != null ? level : LanguageLevel.getDefault();
  }

  public @NotNull PySkeletonGenerator getGenerator() {
    return mySkeletonsGenerator;
  }
}
