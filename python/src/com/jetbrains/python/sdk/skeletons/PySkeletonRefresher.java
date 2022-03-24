// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.skeletons;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PyRemoteSkeletonGeneratorFactory;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.skeleton.PySkeletonHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.util.NlsContexts.ProgressDetails;
import static com.intellij.openapi.util.NlsContexts.ProgressText;

/**
 * Handles a refresh of SDK's skeletons.
 * Does all the heavy lifting calling skeleton generator, managing blacklists, etc.
 * One-time, non-reusable instances.
 * <br/>
 * User: dcheryasov
 */
public class PySkeletonRefresher {
  private static final Logger LOG = Logger.getInstance(PySkeletonRefresher.class);

  private static int ourGeneratingCount = 0;

  @Nullable private final Project myProject;
  @Nullable private final ProgressIndicator myIndicator;
  @NotNull private final Sdk mySdk;
  private String mySkeletonsPath;
  private List<String> myExtraSyspath;
  private int myGeneratorVersion = -1;
  private final PySkeletonGenerator mySkeletonsGenerator;

  public static synchronized boolean isGeneratingSkeletons() {
    return ourGeneratingCount > 0;
  }

  private static synchronized void changeGeneratingSkeletons(int increment) {
    ourGeneratingCount += increment;
  }

  public static void refreshSkeletonsOfSdk(@Nullable Project project,
                                           @Nullable Component ownerComponent,
                                           @Nullable String skeletonsPath,
                                           @NotNull Sdk sdk)
    throws InvalidSdkException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    final String homePath = sdk.getHomePath();
    if (skeletonsPath == null) {
      LOG.info("Could not find skeletons path for SDK path " + homePath);
    }
    else {
      LOG.info("Refreshing skeletons for " + homePath);
      final PySkeletonRefresher refresher = new PySkeletonRefresher(project, ownerComponent, sdk, skeletonsPath, indicator, null);

      changeGeneratingSkeletons(1);
      try {
        final List<String> errors = refresher.regenerateSkeletons();
        if (!errors.isEmpty()) {
          LOG.warn(PyBundle.message("sdk.some.skeletons.failed"));
          for (String moduleName : errors) {
            LOG.warn(moduleName);
          }
        }
      }
      catch (ExecutionException e) {
        LOG.error(e);
      }
      finally {
        changeGeneratingSkeletons(-1);
      }
    }
  }

  /**
   * Creates a new object that refreshes skeletons of given SDK.
   *
   * @param sdk           a Python SDK
   * @param skeletonsPath if known; null means 'determine and create as needed'.
   * @param indicator     to report progress of long operations
   */
  public PySkeletonRefresher(@Nullable Project project,
                             @Nullable Component ownerComponent,
                             @NotNull Sdk sdk,
                             @Nullable String skeletonsPath,
                             @Nullable ProgressIndicator indicator,
                             @Nullable String folder)
    throws InvalidSdkException {
    myProject = project;
    myIndicator = indicator;
    mySdk = sdk;
    mySkeletonsPath = skeletonsPath;
    if (Registry.get("python.use.targets.api").asBoolean()) {
      mySkeletonsGenerator = new PyTargetsSkeletonGenerator(getSkeletonsPath(), mySdk, folder, myProject);
    }
    else if (PythonSdkUtil.isRemote(sdk)) {
      try {
        mySkeletonsGenerator = createRemoteSkeletonGenerator(myProject, ownerComponent, sdk, getSkeletonsPath());
      }
      catch (ExecutionException e) {
        throw new InvalidSdkException(e.getMessage(), e.getCause());
      }
    }
    else {
      mySkeletonsGenerator = new PyLegacySkeletonGenerator(getSkeletonsPath(), mySdk, folder);
    }
  }

  @NotNull
  public List<String> regenerateSkeletons() throws InvalidSdkException, ExecutionException {
    final String skeletonsPath = getSkeletonsPath();
    final File skeletonsDir = new File(skeletonsPath);
    //noinspection ResultOfMethodCallIgnored
    skeletonsDir.mkdirs();

    mySkeletonsGenerator.prepare();

    myGeneratorVersion = readGeneratorVersion();

    final PyPregeneratedSkeletons preGeneratedSkeletons =
      PyPregeneratedSkeletonsProvider.findPregeneratedSkeletonsForSdk(mySdk, myGeneratorVersion);

    final String builtinsFileName = PythonSdkType.getBuiltinsFileName(mySdk);
    final File builtinsFile = new File(skeletonsPath, builtinsFileName);

    final PySkeletonHeader oldHeader = PySkeletonHeader.readSkeletonHeader(builtinsFile);
    final boolean oldOrNonExisting = oldHeader == null || oldHeader.getVersion() == 0;

    if (preGeneratedSkeletons != null && oldOrNonExisting) {
      indicate(PyBundle.message("sdk.gen.unpacking.prebuilt"));
      preGeneratedSkeletons.unpackPreGeneratedSkeletons(getSkeletonsPath());
    }

    indicate(PyBundle.message("sdk.gen.launching.generator"));
    final List<PySkeletonGenerator.GenerationResult> results = updateOrCreateSkeletons();
    final List<String> failedModules = ContainerUtil.mapNotNull(results, result -> {
      if (result.getGenerationStatus() == PySkeletonGenerator.GenerationStatus.FAILED) {
        return result.getModuleName();
      }
      return null;
    });
    final boolean builtinsUpdated = ContainerUtil.exists(results,
                                                         result -> result.getModuleOrigin().equals(PySkeletonHeader.BUILTIN_NAME));

    indicate(PyBundle.message("sdk.gen.reloading"));
    mySkeletonsGenerator.refreshGeneratedSkeletons();

    indicate(PyBundle.message("sdk.gen.cleaning.up"));
    cleanUpSkeletons(skeletonsDir);

    if ((builtinsUpdated || PythonSdkUtil.isRemote(mySdk)) && myProject != null) {
      ApplicationManager.getApplication().invokeLater(() -> DaemonCodeAnalyzer.getInstance(myProject).restart(), myProject.getDisposed());
    }

    return failedModules;
  }

  private static int readGeneratorVersion() {
    File versionFile = PythonHelpersLocator.getHelperFile("generator3/version.txt");
    try (Reader reader = new InputStreamReader(new FileInputStream(versionFile), StandardCharsets.UTF_8)) {
      return PySkeletonHeader.fromVersionString(StreamUtil.readText(reader).trim());
    }
    catch (IOException e) {
      throw new AssertionError("Failed to read generator version from " + versionFile);
    }
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

  private static List<String> calculateExtraSysPath(@NotNull final Sdk sdk, @Nullable final String skeletonsPath) {
    final File skeletons = skeletonsPath != null ? new File(skeletonsPath) : null;

    final VirtualFile userSkeletonsDir = PyUserSkeletonsUtil.getUserSkeletonsDirectory();
    final File userSkeletons = userSkeletonsDir != null ? new File(userSkeletonsDir.getPath()) : null;

    final VirtualFile remoteSourcesDir = PythonSdkUtil.findAnyRemoteLibrary(sdk);
    final File remoteSources = remoteSourcesDir != null ? new File(remoteSourcesDir.getPath()) : null;

    final List<VirtualFile> paths = new ArrayList<>();

    paths.addAll(Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.CLASSES)));

    return ContainerUtil.mapNotNull(paths, file -> {
      if (file.isInLocalFileSystem()) {
        // We compare canonical files, not strings because "c:/some/folder" equals "c:\\some\\bin\\..\\folder\\"
        final File canonicalFile = new File(file.getPath());
        if (canonicalFile.exists() &&
            !FileUtil.filesEqual(canonicalFile, skeletons) &&
            !FileUtil.filesEqual(canonicalFile, userSkeletons) &&
            !FileUtil.filesEqual(canonicalFile, remoteSources)) {
          return file.getPath();
        }
      }
      return null;
    });
  }

  /**
   * Creates if needed all path(s) used to store skeletons of its SDK.
   *
   * @return path name of skeleton dir for the SDK, guaranteed to be already created.
   */
  @NotNull
  public String getSkeletonsPath() throws InvalidSdkException {
    if (mySkeletonsPath == null) {
      mySkeletonsPath = Objects.requireNonNull(PythonSdkUtil.getSkeletonsPath(mySdk));
      final File skeletonsDir = new File(mySkeletonsPath);
      if (!skeletonsDir.exists() && !skeletonsDir.mkdirs()) {
        throw new InvalidSdkException(PyBundle.message("sdk.gen.cannot.create.skeleton.dir", mySkeletonsPath));
      }
    }
    return mySkeletonsPath;
  }

  @NotNull
  private List<PySkeletonGenerator.GenerationResult> updateOrCreateSkeletons() throws InvalidSdkException, ExecutionException {
    final long startTime = System.currentTimeMillis();
    final List<PySkeletonGenerator.GenerationResult> result = mySkeletonsGenerator
      .commandBuilder()
      .extraSysPath(getExtraSyspath())
      .runGeneration(myIndicator);
    finishSkeletonsGeneration();
    LOG.info("Rebuilding skeletons for binaries took " + (System.currentTimeMillis() - startTime) + " ms");
    return result;
  }

  /**
   * For every existing skeleton file, take its module file name,
   * and remove the skeleton if the module file does not exist.
   * Works recursively starting from dir. Removes dirs that become empty.
   */
  private void cleanUpSkeletons(final File dir) {
    indicateMinor(dir.getPath());
    final File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    for (File item : files) {
      if (item.isDirectory()) {
        cleanUpSkeletons(item);
        // was the dir emptied?
        File[] remaining = item.listFiles();
        if (remaining != null && remaining.length == 0) {
          mySkeletonsGenerator.deleteOrLog(item);
        }
        else if (remaining != null && remaining.length == 1) { //clean also if contains only __init__.py
          File lastFile = remaining[0];
          if (PyNames.INIT_DOT_PY.equals(lastFile.getName()) && lastFile.length() == 0) {
            boolean deleted = mySkeletonsGenerator.deleteOrLog(lastFile);
            if (deleted) mySkeletonsGenerator.deleteOrLog(item);
          }
        }
      }
      else if (item.isFile()) {
        // clean up an individual file
        final String itemName = item.getName();
        if (PyNames.INIT_DOT_PY.equals(itemName) && item.length() == 0) continue; // these are versionless
        if (PySkeletonGenerator.BLACKLIST_FILE_NAME.equals(itemName)) continue; // don't touch the blacklist
        if (PySkeletonGenerator.STATE_MARKER_FILE.equals(itemName)) continue;
        if (PythonSdkType.getBuiltinsFileName(mySdk).equals(itemName)) {
          continue;
        }
        final PySkeletonHeader header = PySkeletonHeader.readSkeletonHeader(item);
        String binaryFile = null;
        boolean canLive = header != null;
        if (canLive) {
          binaryFile = header.getBinaryFile();
          canLive = PySkeletonHeader.PREGENERATED.equals(binaryFile) ||
                    PySkeletonHeader.BUILTIN_NAME.equals(binaryFile) ||
                    mySkeletonsGenerator.exists(binaryFile);
        }
        if (!canLive) {
          LOG.debug("Cleaning up skeleton " + item + (binaryFile != null ? " for non existing binary " + binaryFile : ""));
          mySkeletonsGenerator.deleteOrLog(item);
        }
      }
    }
  }

  private void finishSkeletonsGeneration() {
    mySkeletonsGenerator.finishSkeletonsGeneration();
  }


  private List<String> getExtraSyspath() {
    if (myExtraSyspath == null) {
      myExtraSyspath = calculateExtraSysPath(mySdk, mySkeletonsPath);
    }
    return myExtraSyspath;
  }

  public int getGeneratorVersion() {
    if (myGeneratorVersion == -1) {
      myGeneratorVersion = readGeneratorVersion();
    }
    return myGeneratorVersion;
  }

  @NotNull
  public static PySkeletonGenerator createRemoteSkeletonGenerator(@Nullable Project project,
                                                                  Component ownerComponent,
                                                                  @NotNull Sdk sdk,
                                                                  String skeletonsPath) throws ExecutionException {
    PyRemoteSdkAdditionalDataBase sdkAdditionalData = (PyRemoteSdkAdditionalDataBase)sdk.getSdkAdditionalData();
    return PyRemoteSkeletonGeneratorFactory.getInstance(sdkAdditionalData)
      .createRemoteSkeletonGenerator(project, ownerComponent, sdk, skeletonsPath);
  }

  @NotNull
  public PySkeletonGenerator getGenerator() {
    return mySkeletonsGenerator;
  }
}
