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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PatternUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.icons.PythonSdkIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.jetbrains.python.sdk.flavors.PySdkFlavorUtilKt.getFileExecutionError;


/**
 * Flavor is a type of python interpreter stored in {@link PythonSdkAdditionalData}.
 * Each flavor may have specific {@link PyFlavorData} which could be used to run python interpreter.
 *
 * @param <D> is flavor-specific data attached to each SDK.
 */
public abstract class PythonSdkFlavor<D extends PyFlavorData> {
  public static final ExtensionPointName<PythonSdkFlavor<?>> EP_NAME = ExtensionPointName.create("Pythonid.pythonSdkFlavor");
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
   * To provide <pre>PYCHARM_HOSTED</pre> or not. Some libs assume output is tty when this var set, which may lead to DS-4036
   */
  public boolean providePyCharmHosted() {
    return true;
  }

  /**
   * Class of flavor data. Always implement it explicitly
   */
  @NotNull
  public Class<D> getFlavorDataClass() {
    return getEmptyFlavorForBackwardCompatibility();
  }

  /**
   * Some plugins didn't implement {@link #getFlavorDataClass()}
   */
  @SuppressWarnings("unchecked")
  @NotNull
  private Class<D> getEmptyFlavorForBackwardCompatibility() {
    LOG.warn("getFlavorDataClass is not implemented, please implement it");
    return (Class<D>)PyFlavorData.Empty.class;
  }

  /**
   * On local targets some flavours could be detected. It returns path to python interpreters for such cases.
   */
  public @NotNull Collection<@NotNull Path> suggestLocalHomePaths(@Nullable final Module module, @Nullable final UserDataHolder context) {
    return ContainerUtil.map(suggestHomePaths(module, context), Path::of);
  }

  /**
   * @deprecated use {@link #suggestLocalHomePaths(Module, UserDataHolder)}
   */
  @Deprecated
  public Collection<String> suggestHomePaths(@Nullable final Module module, @Nullable final UserDataHolder context) {
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
  @Nullable
  public String envPathParam() {
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
    var error = getErrorIfNotExecutable(fullPath, targetEnvConfig);
    if (error != null) {
      Logger.getInstance(PythonSdkFlavor.class).warn(String.format("%s is not executable: %s", fullPath, error));
    }
    var newValue = error == null;
    ourExecutableFiles.put(id, newValue);
    return newValue;
  }

  @Nullable
  @Nls
  private static String getErrorIfNotExecutable(@NotNull String fullPath, @Nullable TargetEnvironmentConfiguration targetEnvConfig) {
    if (SwingUtilities.isEventDispatchThread()) {
      // Run under progress
      // TODO: use pyModalBlocking when we merge two modules
      return ProgressManager.getInstance()
        .run(new Task.WithResult<@Nullable @Nls String, RuntimeException>(null, PySdkBundle.message("path.validation.wait.path", fullPath),
                                                                          false) {
          @Override
          @Nls
          @Nullable
          protected String compute(@NotNull ProgressIndicator indicator) throws RuntimeException {
            return getFileExecutionError(fullPath, targetEnvConfig);
          }
        });
    }
    else {
      return getFileExecutionError(fullPath, targetEnvConfig);
    }
  }

  @NotNull
  private static String getIdForCache(@NotNull String fullPath, @Nullable TargetEnvironmentConfiguration configuration) {
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

  public static @NotNull List<PythonSdkFlavor<?>> getApplicableFlavors() {
    return getApplicableFlavors(true);
  }

  public static @NotNull List<PythonSdkFlavor<?>> getApplicableFlavors(boolean addPlatformIndependent) {
    List<PythonSdkFlavor<?>> result = new ArrayList<>();
    for (PythonSdkFlavor<?> flavor : EP_NAME.getExtensionList()) {
      if (flavor.isApplicable() || (addPlatformIndependent && flavor.isPlatformIndependent())) {
        result.add(flavor);
      }
    }

    result.addAll(getPlatformFlavorsFromExtensions(addPlatformIndependent));

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

  @Nullable
  public static PythonSdkFlavor<?> getFlavor(@NotNull final Sdk sdk) {
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
   * @deprecated SDK path is not enough to get flavor, use {@link #getFlavor(Sdk)} instead
   */
  @Deprecated
  @Nullable
  public static PythonSdkFlavor<?> getFlavor(@Nullable String sdkPath) {
    if (sdkPath == null) return null;

    for (PythonSdkFlavor<?> flavor : getApplicableFlavors()) {
      if (flavor.isValidSdkHome(sdkPath)) {
        return flavor;
      }
    }
    return null;
  }

  /**
   * @deprecated SDK path is not enough to get flavor, use {@link #getFlavor(Sdk)} instead
   */
  @Deprecated
  @Nullable
  public static PythonSdkFlavor<?> getPlatformIndependentFlavor(@Nullable final String sdkPath) {
    if (sdkPath == null) return null;

    for (PythonSdkFlavor<?> flavor : getPlatformIndependentFlavors()) {
      if (flavor.isValidSdkHome(sdkPath)) {
        return flavor;
      }
    }

    for (PythonSdkFlavor<?> flavor : getPlatformFlavorsFromExtensions(true)) {
      if (flavor.isValidSdkHome(sdkPath)) {
        return flavor;
      }
    }
    return null;
  }


  /**
   * @param path path to check.
   * @return true if paths points to a valid home.
   * Checks if the path is the name of a Python interpreter of this flavor.
   * @deprecated path is not enough, use {@link #sdkSeemsValid(Sdk, PyFlavorData, TargetEnvironmentConfiguration)}
   */
  @Deprecated
  public boolean isValidSdkHome(@NotNull String path) {
    File file = new File(path);
    return file.isFile() && isValidSdkPath(file);
  }


  /**
   * It only validates path for local target, hence use {@link #sdkSeemsValid(Sdk, PyFlavorData, TargetEnvironmentConfiguration)} instead
   */
  public boolean isValidSdkPath(@NotNull File file) {
    return StringUtil.toLowerCase(FileUtilRt.getNameWithoutExtension(file.getName())).contains("python");
  }

  @Nullable
  public String getVersionString(@Nullable String sdkHome) {
    if (sdkHome == null) {
      return null;
    }
    final String runDirectory = new File(sdkHome).getParent();
    final ProcessOutput processOutput = PySdkUtil.getProcessOutput(runDirectory, new String[]{sdkHome, getVersionOption()}, 10000);
    return getVersionStringFromOutput(processOutput);
  }

  @Nullable
  public String getVersionStringFromOutput(@NotNull ProcessOutput processOutput) {
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

  @Nullable
  public String getVersionStringFromOutput(@NotNull String output) {
    return PatternUtil.getFirstMatch(Arrays.asList(StringUtil.splitByLines(output)), VERSION_RE);
  }

  public @NotNull String getVersionOption() {
    return "-V";
  }

  public @NotNull Collection<String> getExtraDebugOptions() {
    return Collections.emptyList();
  }

  public void initPythonPath(@NotNull GeneralCommandLine cmd, boolean passParentEnvs, @NotNull Collection<String> path) {
    initPythonPath(path, passParentEnvs, cmd.getEnvironment());
  }

  @NotNull
  public abstract String getName();

  /**
   * Unique flavor name to be stored in persistence storage. Do not change value not to break compatibility.
   */
  @NotNull
  public String getUniqueId() {
    return getClass().getSimpleName();
  }

  @NotNull
  public LanguageLevel getLanguageLevel(@NotNull Sdk sdk) {
    return getLanguageLevelFromVersionString(sdk.getVersionString());
  }

  @NotNull
  public LanguageLevel getLanguageLevel(@NotNull String sdkHome) {
    return getLanguageLevelFromVersionString(getVersionString(sdkHome));
  }

  @NotNull
  public LanguageLevel getLanguageLevelFromVersionString(@Nullable String version) {
    final String prefix = getName() + " ";
    if (version != null && version.startsWith(prefix)) {
      return LanguageLevel.fromPythonVersion(version.substring(prefix.length()));
    }
    return LanguageLevel.getDefault();
  }

  public @NotNull Icon getIcon() {
    return PythonSdkIcons.Python;
  }

  public void initPythonPath(@NotNull Collection<String> path, boolean passParentEnvs, @NotNull Map<String, String> env) {
    PythonEnvUtil.initPythonPath(env, passParentEnvs, path);
  }

  public @Nullable VirtualFile getSdkPath(@NotNull VirtualFile path) {
    return path;
  }

  @Nullable
  public CommandLinePatcher commandLinePatcher() {
    return null;
  }

  /**
   * Could be called intentionally if another component suppose that
   * there could be new data provided by a flavor.
   */
  public void dropCaches() {
  }

  public final static class UnknownFlavor extends PythonSdkFlavor<PyFlavorData.Empty> {

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
