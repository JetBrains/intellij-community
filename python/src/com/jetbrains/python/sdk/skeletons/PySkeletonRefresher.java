/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.sdk.skeletons;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.buildout.BuildoutFacet;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PyRemoteSkeletonGeneratorFactory;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.skeleton.PySkeletonHeader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.sdk.skeleton.PySkeletonHeader.fromVersionString;

/**
 * Handles a refresh of SDK's skeletons.
 * Does all the heavy lifting calling skeleton generator, managing blacklists, etc.
 * One-time, non-reusable instances.
 * <br/>
 * User: dcheryasov
 */
public class PySkeletonRefresher {
  private static final Logger LOG = Logger.getInstance(PySkeletonRefresher.class);


  @Nullable private final Project myProject;
  private @Nullable final ProgressIndicator myIndicator;
  @NotNull private final Sdk mySdk;
  private String mySkeletonsPath;

  @NonNls public static final String BLACKLIST_FILE_NAME = ".blacklist";
  private final static Pattern BLACKLIST_LINE = Pattern.compile("^([^=]+) = (\\d+\\.\\d+) (\\d+)\\s*$");
  // we use the equals sign after filename so that we can freely include space in the filename

  private static int ourGeneratingCount = 0;

  private List<String> myExtraSyspath;
  private int myGeneratorVersion;
  private Map<String, Pair<Integer, Long>> myBlacklist;

  private final PySkeletonGenerator mySkeletonsGenerator;

  public static synchronized boolean isGeneratingSkeletons() {
    return ourGeneratingCount > 0;
  }

  private static synchronized void changeGeneratingSkeletons(int increment) {
    ourGeneratingCount += increment;
  }

  public List<String> regenerateSkeletons(@Nullable SkeletonVersionChecker checker) throws InvalidSdkException, ExecutionException {
    final List<String> errorList = new SmartList<>();
    final String homePath = mySdk.getHomePath();
    final String skeletonsPath = getSkeletonsPath();
    final File skeletonsDir = new File(skeletonsPath);
    if (!skeletonsDir.exists()) {
      //noinspection ResultOfMethodCallIgnored
      skeletonsDir.mkdirs();
    }
    final String readablePath = FileUtil.getLocationRelativeToUserHome(homePath);

    if (checker != null && checker.isPregenerated()) {
      mySkeletonsGenerator.setPrebuilt(true);
    }

    mySkeletonsGenerator.prepare();
    myBlacklist = loadBlacklist();

    updateOrCreateSkeletons();

    indicate(PyBundle.message("sdk.gen.reloading"));
    mySkeletonsGenerator.refreshGeneratedSkeletons();

    indicate(PyBundle.message("sdk.gen.cleaning.$0", readablePath));
    cleanUpSkeletons(skeletonsDir);

    ApplicationManager.getApplication().invokeLater(() -> DaemonCodeAnalyzer.getInstance(myProject).restart(), myProject.getDisposed());

    return errorList;
  }

  private static void logErrors(@NotNull final Map<String, List<String>> errors, @NotNull final List<String> failedSdks,
                                @NotNull final String message) {
    LOG.warn(PyBundle.message("sdk.some.skeletons.failed"));
    LOG.warn(message);

    if (failedSdks.size() > 0) {
      LOG.warn(PyBundle.message("sdk.error.dialog.failed.sdks"));
      LOG.warn(StringUtil.join(failedSdks, ", "));
    }

    if (errors.size() > 0) {
      LOG.warn(PyBundle.message("sdk.error.dialog.failed.modules"));
      for (String sdkName : errors.keySet()) {
        for (String moduleName : errors.get(sdkName)) {
          LOG.warn(moduleName);
        }
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
    if (PythonSdkUtil.isRemote(sdk)) {
      try {
        mySkeletonsGenerator = createRemoteSkeletonGenerator(myProject, ownerComponent, sdk, getSkeletonsPath());
      }
      catch (ExecutionException e) {
        throw new InvalidSdkException(e.getMessage(), e.getCause());
      }
    }
    else {
      mySkeletonsGenerator = new PySkeletonGenerator(getSkeletonsPath(), mySdk, folder);
    }
  }

  private void indicate(String msg) {
    LOG.debug("Progress message: " + msg);
    if (myIndicator != null) {
      myIndicator.checkCanceled();
      myIndicator.setText(msg);
      myIndicator.setText2("");
    }
  }

  private void indicateMinor(String msg) {
    LOG.debug("Progress message (minor): " + msg);
    if (myIndicator != null) {
      myIndicator.setText2(msg);
    }
  }

  private void checkCanceled() {
    if (myIndicator != null) {
      myIndicator.checkCanceled();
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
    paths.addAll(BuildoutFacet.getExtraPathForAllOpenModules());

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
      mySkeletonsPath = PythonSdkType.getSkeletonsPath(PathManager.getSystemPath(), mySdk.getHomePath());
      final File skeletonsDir = new File(mySkeletonsPath);
      if (!skeletonsDir.exists() && !skeletonsDir.mkdirs()) {
        throw new InvalidSdkException("Can't create skeleton dir " + mySkeletonsPath);
      }
    }
    return mySkeletonsPath;
  }

  @NotNull
  private List<PySkeletonGenerator.GenerationResult> updateOrCreateSkeletons() throws InvalidSdkException, ExecutionException {
    final long startTime = System.currentTimeMillis();
    final List<PySkeletonGenerator.GenerationResult> result = mySkeletonsGenerator
      .withExtraSysPath(getExtraSyspath())
      .runGeneration(myIndicator);
    finishSkeletonsGeneration();
    LOG.info("Rebuilding skeletons for binaries took " + (System.currentTimeMillis() - startTime) + " ms");
    return result;
  }


  private Map<String, Pair<Integer, Long>> loadBlacklist() {
    Map<String, Pair<Integer, Long>> ret = new HashMap<>();
    File blacklistFile = new File(mySkeletonsPath, BLACKLIST_FILE_NAME);
    if (blacklistFile.exists() && blacklistFile.canRead()) {
      Reader input;
      try {
        input = new FileReader(blacklistFile);
        LineNumberReader lines = new LineNumberReader(input);
        try {
          String line;
          do {
            line = lines.readLine();
            if (line != null && line.length() > 0 && line.charAt(0) != '#') { // '#' begins a comment
              Matcher matcher = BLACKLIST_LINE.matcher(line);
              boolean notParsed = true;
              if (matcher.matches()) {
                final int version = fromVersionString(matcher.group(2));
                if (version > 0) {
                  try {
                    final long timestamp = Long.parseLong(matcher.group(3));
                    final String filename = matcher.group(1);
                    ret.put(filename, new Pair<>(version, timestamp));
                    notParsed = false;
                  }
                  catch (NumberFormatException ignore) {
                  }
                }
              }
              if (notParsed) LOG.warn("In blacklist at " + mySkeletonsPath + " strange line '" + line + "'");
            }
          }
          while (line != null);
        }
        catch (IOException ex) {
          LOG.warn("Failed to read blacklist in " + mySkeletonsPath, ex);
        }
        finally {
          lines.close();
        }
      }
      catch (IOException ignore) {
      }
    }
    return ret;
  }

  private static void storeBlacklist(File skeletonDir, Map<String, Pair<Integer, Long>> blacklist) {
    File blacklistFile = new File(skeletonDir, BLACKLIST_FILE_NAME);
    PrintWriter output;
    try {
      output = new PrintWriter(blacklistFile);
      try {
        output.println("# PyCharm failed to generate skeletons for these modules.");
        output.println("# These skeletons will be re-generated automatically");
        output.println("# when a newer module version or an updated generator becomes available.");
        // each line:   filename = version.string timestamp
        for (String fname : blacklist.keySet()) {
          Pair<Integer, Long> data = blacklist.get(fname);
          output.print(fname);
          output.print(" = ");
          output.print(SkeletonVersionChecker.toVersionString(data.getFirst()));
          output.print(" ");
          output.print(data.getSecond());
          output.println();
        }
      }
      finally {
        output.close();
      }
    }
    catch (IOException ex) {
      LOG.warn("Failed to store blacklist in " + skeletonDir.getPath(), ex);
    }
  }

  private static void removeBlacklist(File skeletonDir) {
    File blacklistFile = new File(skeletonDir, BLACKLIST_FILE_NAME);
    if (blacklistFile.exists()) {
      boolean okay = blacklistFile.delete();
      if (!okay) LOG.warn("Could not delete blacklist file in " + skeletonDir.getPath());
    }
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
        if (BLACKLIST_FILE_NAME.equals(itemName)) continue; // don't touch the blacklist
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

  public static void refreshSkeletonsOfSdk(@Nullable Project project,
                                           @Nullable Component ownerComponent,
                                           @Nullable String skeletonsPath,
                                           @NotNull Sdk sdk)
    throws InvalidSdkException {
    final Map<String, List<String>> errors = new TreeMap<>();
    final List<String> failedSdks = new SmartList<>();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    final String homePath = sdk.getHomePath();
    if (skeletonsPath == null) {
      LOG.info("Could not find skeletons path for SDK path " + homePath);
    }
    else {
      LOG.info("Refreshing skeletons for " + homePath);
      SkeletonVersionChecker checker = new SkeletonVersionChecker(0); // this default version won't be used
      final PySkeletonRefresher refresher = new PySkeletonRefresher(project, ownerComponent, sdk, skeletonsPath, indicator, null);

      changeGeneratingSkeletons(1);
      try {
        List<String> sdkErrors = refresher.regenerateSkeletons(checker);
        if (sdkErrors.size() > 0) {
          String sdkName = sdk.getName();
          List<String> knownErrors = errors.get(sdkName);
          if (knownErrors == null) {
            errors.put(sdkName, sdkErrors);
          }
          else {
            knownErrors.addAll(sdkErrors);
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
    if (failedSdks.size() > 0 || errors.size() > 0) {
      int module_errors = 0;
      for (String sdk_name : errors.keySet()) module_errors += errors.get(sdk_name).size();
      String message;
      if (failedSdks.size() > 0) {
        message = PyBundle.message("sdk.errorlog.$0.mods.fail.in.$1.sdks.$2.completely", module_errors, errors.size(), failedSdks.size());
      }
      else {
        message = PyBundle.message("sdk.errorlog.$0.mods.fail.in.$1.sdks", module_errors, errors.size());
      }
      logErrors(errors, failedSdks, message);
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
