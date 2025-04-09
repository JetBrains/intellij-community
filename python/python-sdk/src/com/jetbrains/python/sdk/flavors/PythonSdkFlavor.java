// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.target.TargetConfigurationWithId;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PatternUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.icons.PythonPsiApiIcons;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.sdk.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.jetbrains.python.venvReader.ResolveUtilKt.tryResolvePath;
import static com.jetbrains.python.sdk.flavors.PySdkFlavorUtilKt.getFileExecutionError;
import static com.jetbrains.python.sdk.flavors.PySdkFlavorUtilKt.getFileExecutionErrorOnEdt;


/**
 * Flavor is a type of python interpreter stored in {@link PythonSdkAdditionalData}.
 * Each flavor may have specific {@link PyFlavorData} which could be used to run python interpreter.
 *
 * @param <D> is flavor-specific data attached to each SDK.
 */
public abstract class PythonSdkFlavor<D extends PyFlavorData> {
  public static final ExtensionPointName<PythonSdkFlavor<?>> EP_NAME = ExtensionPointName.create("Pythonid.pythonSdkFlavor");
  /**
   * <code>
   * Python 3.11
   * </code>
   */
  @ApiStatus.Internal
  public static final String PYTHON_VERSION_STRING_PREFIX = "Python ";
  /**
   * To prevent log pollution and slowness, we cache every {@link #isFileExecutable(String, TargetEnvironmentConfiguration)} call
   * and only log it once
   */
  private static final Cache<@NotNull String, @NotNull Boolean> ourExecutableFiles = Caffeine.newBuilder()
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .maximumSize(1000)
    .build();

  private static final Pattern VERSION_RE = Pattern.compile("(Python \\S+).*");
  private static final Logger LOG = Logger.getInstance(PythonSdkFlavor.class);
  /**
   * <code>
   * python --version
   * </code>
   */
  public static final String PYTHON_VERSION_ARG = "--version";


  /**
   * To provide <pre>PYCHARM_HOSTED</pre> or not. Some libs assume output is tty when this var set, which may lead to DS-4036
   */
  public boolean providePyCharmHosted() {
    return true;
  }

  /**
   * Class of flavor data. Always implement it explicitly
   */
  public @NotNull Class<D> getFlavorDataClass() {
    return getEmptyFlavorForBackwardCompatibility();
  }

  /**
   * Some plugins didn't implement {@link #getFlavorDataClass()}
   */
  @SuppressWarnings("unchecked")
  private @NotNull Class<D> getEmptyFlavorForBackwardCompatibility() {
    LOG.warn("getFlavorDataClass is not implemented, please implement it");
    return (Class<D>)PyFlavorData.Empty.class;
  }

  /**
   * On local targets some flavours could be detected. It returns path to python interpreters for such cases.
   */
  @RequiresBackgroundThread
  public @NotNull Collection<@NotNull Path> suggestLocalHomePaths(final @Nullable Module module, final @Nullable UserDataHolder context) {
    return ContainerUtil.map(suggestHomePaths(module, context), Path::of);
  }

  /**
   * @deprecated use {@link #suggestLocalHomePaths(Module, UserDataHolder)}
   */
  @Deprecated(forRemoval = true)
  public Collection<String> suggestHomePaths(final @Nullable Module module, final @Nullable UserDataHolder context) {
    return Collections.emptyList();
  }


  /**
   * Flavor is added to result in {@link #getApplicableFlavors()} if this method returns true.
   * If the only condition is independence of platform, then {@link #isPlatformIndependent()} should be used.
   *
   * @return whether this flavor is applicable
   */
  public boolean isApplicable() {
    return false;
  }

  /**
   * Used for distinguishing platform flavors from platform-independent ones in {@link #getPlatformIndependentFlavors()}.
   *
   * @return whether the flavor is platform independent
   */
  public boolean isPlatformIndependent() {
    return false;
  }

  /**
   * Some flavors need current folder to be put in its environment variable.
   *
   * @return name of env variable to contain current folder.
   * {@code null} if the flavor doesn't need it
   */
  public @Nullable String envPathParam() {
    return null;
  }

  /**
   * This flavour doesn't need special data and could be used with {@link PyFlavorData.Empty}
   */
  public boolean supportsEmptyData() {
    return true;
  }

  /**
   * If method is true, sdk may or may not be usable, but false means SDK is invalid
   *
   * @param targetConfig null of local target
   */
  public boolean sdkSeemsValid(@NotNull Sdk sdk, @NotNull D flavorData, @Nullable TargetEnvironmentConfiguration targetConfig) {
    // Most flavours just execute homePath on target, hence file must be executable
    var path = sdk.getHomePath();
    if (path == null) {
      LOG.warn("Sdk doesn't have homepath:" + sdk.getName());
      return false;
    }
    return isFileExecutable(path, targetConfig);
  }

  /**
   * False means file is not executable, but true means it is executable, or we do not know.
   *
   * @param fullPath full path on target
   */
  protected static boolean isFileExecutable(@NotNull String fullPath, @Nullable TargetEnvironmentConfiguration targetEnvConfig) {
    var id = getIdForCache(fullPath, targetEnvConfig);
    Boolean executable = ourExecutableFiles.getIfPresent(id);
    if (executable != null) {
      return executable;
    }
    var error = SwingUtilities.isEventDispatchThread()
                ? getFileExecutionErrorOnEdt(fullPath, targetEnvConfig)
                : getFileExecutionError(fullPath, targetEnvConfig);
    if (error != null) {
      Logger.getInstance(PythonSdkFlavor.class).warn(String.format("%s is not executable: %s", fullPath, error));
    }
    var newValue = error == null;
    ourExecutableFiles.put(id, newValue);
    return newValue;
  }


  public static void clearExecutablesCache() {
    ourExecutableFiles.invalidateAll();
  }

  private static @NotNull String getIdForCache(@NotNull String fullPath, @Nullable TargetEnvironmentConfiguration configuration) {
    var builder = new StringBuilder(fullPath);
    builder.append(" ");
    if (configuration instanceof TargetConfigurationWithId) {
      var typeAndTargetId = ((TargetConfigurationWithId)configuration).getTargetAndTypeId();
      builder.append(typeAndTargetId.component1().toString());
      builder.append(typeAndTargetId.getSecond());
    }
    else if (configuration != null) {
      builder.append(configuration.getClass().getName());
    }
    else {
      builder.append("local");
    }
    return builder.toString();
  }

  /**
   * List of flavors starting from platform-independent, so venv flavor goes before unix or windows flavor.
   * That could be used to find the first flavor that is {@link PythonSdkFlavor#isValidSdkPath(File)} for example
   */
  public static @NotNull List<PythonSdkFlavor<?>> getApplicableFlavors(boolean addPlatformIndependent) {
    List<PythonSdkFlavor<?>> result = new ArrayList<>();
    for (PythonSdkFlavor<?> flavor : EP_NAME.getExtensionList()) {
      if (flavor.isApplicable() || (addPlatformIndependent && flavor.isPlatformIndependent())) {
        result.add(flavor);
      }
    }

    result.addAll(getPlatformFlavorsFromExtensions(addPlatformIndependent));

    // Sort flavors to make venv go before unix/windows, see method doc
    if (addPlatformIndependent) {
      result.sort((f1, f2) -> Boolean.compare(f2.isPlatformIndependent(), f1.isPlatformIndependent()));
    }
    return result;
  }

  public static @NotNull List<PythonSdkFlavor<?>> getPlatformFlavorsFromExtensions(boolean isIndependent) {
    List<PythonSdkFlavor<?>> result = new ArrayList<>();
    for (PythonFlavorProvider provider : PythonFlavorProvider.EP_NAME.getExtensionList()) {
      PythonSdkFlavor<?> flavor = provider.getFlavor(isIndependent);
      if (flavor != null) {
        result.add(flavor);
      }
    }
    return result;
  }

  public static @NotNull List<PythonSdkFlavor<?>> getPlatformIndependentFlavors() {
    List<PythonSdkFlavor<?>> result = new ArrayList<>();
    for (PythonSdkFlavor<?> flavor : EP_NAME.getExtensionList()) {
      if (flavor.isPlatformIndependent()) {
        result.add(flavor);
      }
    }

    return result;
  }

  public static @Nullable PythonSdkFlavor<?> getFlavor(final @NotNull Sdk sdk) {
    final SdkAdditionalData data = sdk.getSdkAdditionalData();
    if (data instanceof PythonSdkAdditionalData) {
      return ((PythonSdkAdditionalData)data).getFlavor();
    }
    if (data instanceof PyRemoteSdkAdditionalDataMarker) {
      return null;
    }
    return getFlavor(sdk.getHomePath());
  }

  /**
   * @deprecated SDK path is not enough to get flavor, use {@link #getFlavor(Sdk)} instead.
   * if you do not have sdk yet, and you want to guess the flavor, use {@link #tryDetectFlavorByLocalPath(Path)}
   */
  //No warning yet as there are usages: to be fixed
  @Deprecated(forRemoval = true)
  @RequiresBackgroundThread(generateAssertion = false)
  public static @Nullable PythonSdkFlavor<?> getFlavor(@Nullable String sdkPath) {
    if (sdkPath == null || CustomSdkHomePattern.isCustomPythonSdkHomePath(sdkPath)) return null;
    return tryDetectFlavorByLocalPath(sdkPath);
  }

  /**
   * Detects {@link PythonSdkFlavor} for local python path
   */
  @RequiresBackgroundThread(generateAssertion = false) //No warning yet as there are usages: to be fixed
  public static @Nullable PythonSdkFlavor<?> tryDetectFlavorByLocalPath(@NotNull String sdkPath) {
    // Iterate over all flavors starting with platform-independent (like venv): see `getApplicableFlavors` doc.
    // Order is important as venv must have priority over unix/windows
    for (PythonSdkFlavor<?> flavor : getApplicableFlavors(true)) {
      if (flavor.isValidSdkPath(sdkPath)) {
        return flavor;
      }
    }
    return null;
  }

  /**
   * @deprecated SDK path is not enough to get flavor, use {@link #getFlavor(Sdk)} instead
   */
  @Deprecated(forRemoval = true)
  public static @Nullable PythonSdkFlavor<?> getPlatformIndependentFlavor(final @Nullable String sdkPath) {
    if (sdkPath == null) {
      return null;
    }

    for (PythonSdkFlavor<?> flavor : getPlatformIndependentFlavors()) {
      if (flavor.isValidSdkPath(sdkPath)) {
        return flavor;
      }
    }

    for (PythonSdkFlavor<?> flavor : getPlatformFlavorsFromExtensions(true)) {
      if (flavor.isValidSdkPath(sdkPath)) {
        return flavor;
      }
    }
    return null;
  }

  /**
   * It only validates path for local target, hence use {@link #sdkSeemsValid(Sdk, PyFlavorData, TargetEnvironmentConfiguration)} instead
   */
  public boolean isValidSdkPath(@NotNull String pathStr) {
    Path path = tryResolvePath(pathStr);
    if (path == null) {
      return false;
    }

    return Files.exists(path) && Files.isExecutable(path);
  }

  /**
   * @param sdkHome
   * @return
   * @deprecated use {@link #getVersionStringStatic(String)}
   */
  //because of process output
  @Deprecated(forRemoval = true)
  @RequiresBackgroundThread(generateAssertion = false)
  public @Nullable String getVersionString(@Nullable String sdkHome) {
    return getVersionStringStatic(sdkHome);
  }

  //because of process output
  @RequiresBackgroundThread(generateAssertion = false)
  public static @Nullable String getVersionStringStatic(@Nullable String sdkHome) {
    if (sdkHome == null) {
      return null;
    }
    final String runDirectory = new File(sdkHome).getParent();
    final ProcessOutput processOutput = PySdkUtil.getProcessOutput(runDirectory, new String[]{sdkHome, PYTHON_VERSION_ARG}, 10000);
    return getVersionStringFromOutput(processOutput);
  }

  public static @Nullable String getVersionStringFromOutput(@NotNull ProcessOutput processOutput) {
    if (processOutput.getExitCode() != 0) {
      String errors = processOutput.getStderr();
      if (StringUtil.isEmpty(errors)) {
        errors = processOutput.getStdout();
      }
      LOG.warn("Couldn't get interpreter version: process exited with code " + processOutput.getExitCode() + "\n" + errors);
      return null;
    }
    final String result = getVersionStringFromOutput(processOutput.getStderr());
    if (result != null) {
      return result;
    }
    return getVersionStringFromOutput(processOutput.getStdout());
  }

  public static @Nullable String getVersionStringFromOutput(@NotNull String output) {
    return PatternUtil.getFirstMatch(Arrays.asList(StringUtil.splitByLines(output)), VERSION_RE);
  }

  public @NotNull Collection<String> getExtraDebugOptions() {
    return Collections.emptyList();
  }

  public void initPythonPath(@NotNull GeneralCommandLine cmd, boolean passParentEnvs, @NotNull Collection<String> path) {
    initPythonPath(path, passParentEnvs, cmd.getEnvironment());
  }

  public abstract @NotNull String getName();

  /**
   * Unique flavor name to be stored in persistence storage. Do not change value not to break compatibility.
   */
  public @NotNull String getUniqueId() {
    return getClass().getSimpleName();
  }

  public @NotNull LanguageLevel getLanguageLevel(@NotNull Sdk sdk) {
    return getLanguageLevelFromVersionStringStatic(sdk.getVersionString());
  }

  //because of process output
  @RequiresBackgroundThread(generateAssertion = false)
  public @NotNull LanguageLevel getLanguageLevel(@NotNull String sdkHome) {
    return getLanguageLevelFromVersionStringStatic(getVersionString(sdkHome));
  }


  /**
   * Returns wrong language level when argument is null which isn't probably what you except.
   * Be sure to check argument for null
   *
   * @deprecated use {@link #getLanguageLevelFromVersionStringStatic(String)}
   */
  @Deprecated(forRemoval = true)
  public @NotNull LanguageLevel getLanguageLevelFromVersionString(@Nullable String version) {
    return getLanguageLevelFromVersionStringStatic(version);
  }

  /**
   * Returns wrong language level when argument is null which isn't probably what you except.
   * Be sure to check argument for null.
   * If string can't be parsed -- returns default.
   * <p>
   * Consider using {@link #getLanguageLevelFromVersionStringStaticSafe(String...)}
   */
  public static @NotNull LanguageLevel getLanguageLevelFromVersionStringStatic(@Nullable String version) {
    if (version == null) {
      return LanguageLevel.getDefault();
    }
    var result = getLanguageLevelFromVersionStringStaticSafe(version);
    return (result == null) ? LanguageLevel.getDefault() : result;
  }

  /**
   * For <code>python --version</code> output (i.e <code>Python 3.12</code>) returns {@link LanguageLevel}.
   * Typical usage: call `python --version`, trim, and provide here.
   *
   * @param versionString output to look language level for
   * @return level or null if no parsable output was found
   */
  public static @Nullable LanguageLevel getLanguageLevelFromVersionStringStaticSafe(@NotNull String versionString) {
    if (versionString.startsWith(PYTHON_VERSION_STRING_PREFIX)) {
      return LanguageLevel.fromPythonVersionSafe(versionString.substring(PYTHON_VERSION_STRING_PREFIX.length()));
    }
    return null;
  }

  public @NotNull Icon getIcon() {
    return PythonPsiApiIcons.Python;
  }

  public void initPythonPath(@NotNull Collection<String> path, boolean passParentEnvs, @NotNull Map<String, String> env) {
    PythonEnvUtil.initPythonPath(env, passParentEnvs, path);
  }

  public @Nullable VirtualFile getSdkPath(@NotNull VirtualFile path) {
    return path;
  }

  public @Nullable CommandLinePatcher commandLinePatcher() {
    return null;
  }

  /**
   * Could be called intentionally if another component suppose that
   * there could be new data provided by a flavor.
   */
  public void dropCaches() {
  }

  public static final class UnknownFlavor extends PythonSdkFlavor<PyFlavorData.Empty> {

    public static final UnknownFlavor INSTANCE = new UnknownFlavor();

    private UnknownFlavor() {

    }

    @Override
    public @NotNull Class<PyFlavorData.Empty> getFlavorDataClass() {
      return PyFlavorData.Empty.class;
    }

    @Override
    public @NotNull String getName() {
      return "";
    }
  }
}
