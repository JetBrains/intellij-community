// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.intellij.python.sdk.backend.PyInterpreterRefsKt;
import com.jetbrains.python.sdk.add.v2.PathHolder;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.python.community.execService.python.VersionParserKt;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.jetbrains.python.parser.icons.PythonParserIcons;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.sdk.CustomSdkHomePattern;
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.jetbrains.python.PythonBinaryKt.PYTHON_VERSION_ARG;


/**
 * Flavor is a type of python interpreter stored in {@link PythonSdkAdditionalData}.
 * Each flavor may have specific {@link PyFlavorData} which could be used to run python interpreter.
 *
 * @param <D> is flavor-specific data attached to each SDK.
 */
public abstract class PythonSdkFlavor<D extends PyFlavorData> {
  @ApiStatus.Internal
  public static final ExtensionPointName<PythonSdkFlavor<?>> EP_NAME = ExtensionPointName.create("Pythonid.pythonSdkFlavor");

  private static final Logger LOG = Logger.getInstance(PythonSdkFlavor.class);


  /**
   * To provide <pre>PYCHARM_HOSTED</pre> or not. Some libs assume output is tty when this var set, which may lead to DS-4036
   */
  @ApiStatus.Internal
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
   * The {@link com.intellij.python.pytools.common.FusId} value of the tool that manages the environments of this
   * flavor, which names them in a {@link com.intellij.python.sdk.common.PyInterpreterRef}. The default is
   * {@code pip}, for a flavor no tool manages, such as a system interpreter or a plain venv. Read it with
   * {@code PythonSdkFlavor.manager}.
   * <p>
   * It is a string here, because Java cannot return the value class. A Kotlin override that returns the value class
   * does not override this method on the JVM, so the flavor then gets the default.
   */
  @ApiStatus.Internal
  public @NotNull String getManagerId() {
    return PyInterpreterRefsKt.PIP_MANAGER_ID;
  }

  /**
   * The {@link com.intellij.python.sdk.common.PyEnvRef} that the tool of this flavor gives the environment whose
   * interpreter is {@code pythonBinary} and whose flavor data is {@code data}, such as the environment name for hatch.
   * {@code null} when the tool names the environment by its binary path, which is the default: see
   * {@link PyInterpreterRefsKt#interpreterRefOf(PathHolder, PythonSdkAdditionalData,
   * com.intellij.python.sdk.common.PyInterpreterRef.Mode)}. It is a string here, because Java cannot return the
   * value class.
   */
  @ApiStatus.Internal
  public @Nullable String toolEnvRefOf(@NotNull PathHolder pythonBinary, @NotNull D data) {
    return null;
  }

  @ApiStatus.Internal
  public static final class AdditionalDataMigration<D extends PyFlavorData> {
    private final @NotNull D myFlavorData;
    private final @Nullable Path myWorkingDirectory;

    public AdditionalDataMigration(@NotNull D flavorData, @Nullable Path workingDirectory) {
      myFlavorData = flavorData;
      myWorkingDirectory = workingDirectory;
    }

    public @NotNull D getFlavorData() {
      return myFlavorData;
    }

    public @Nullable Path getWorkingDirectory() {
      return myWorkingDirectory;
    }
  }

  @ApiStatus.Internal
  public @NotNull AdditionalDataMigration<D> migrateAdditionalData(@NotNull PythonSdkAdditionalData additionalData, @NotNull D data) {
    return new AdditionalDataMigration<>(data, null);
  }

  @ApiStatus.Internal
  public @NotNull D withWorkingDirectory(@NotNull D data, @NotNull Path workingDirectory) {
    return data;
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
   * Flavors that are aware of some system pythons must return them there.
   */
  @RequiresBackgroundThread(generateAssertion = false)
  protected @NotNull Collection<@NotNull Path> suggestLocalHomePathsImpl(final @Nullable Module module,
                                                                         final @Nullable UserDataHolder context) {
    return Collections.emptyList();
  }

  /**
   * On local targets some flavors could be detected. It returns a path to python interpreters for such cases.
   */
  @RequiresBackgroundThread(generateAssertion = false)
  public final @NotNull Collection<@NotNull Path> suggestLocalHomePaths(final @Nullable Module module,
                                                                        final @Nullable UserDataHolder context) {
    return suggestLocalHomePathsImpl(module, context).stream().filter(path -> {
      var flavor = tryDetectFlavorByLocalPath(path);
      boolean correctFlavor = flavor != null && flavor.getClass().equals(getClass());
      // Some flavors might report foreign pythons: e.g. Windows might find conda on PATH.
      if (!correctFlavor) {
        LOG.info(String.format("Path %s has a wrong flavor, not %s, skipping", path, this));
        return false;
      }
      return true;
    }).collect(Collectors.toSet()).stream().sorted().toList();
  }

  /**
   * Flavor is added to result in {@link #getApplicableFlavors()} if this method returns true.
   * If the only condition is independence of platform, then {@link #isPlatformIndependent()} should be used.
   *
   * @return whether this flavor is applicable
   */
  @ApiStatus.Internal
  public boolean isApplicable() {
    return false;
  }

  /**
   * Used for distinguishing platform flavors from platform-independent ones in {@link #getApplicableFlavors(boolean)}.
   *
   * @return whether the flavor is platform independent
   */
  @ApiStatus.Internal
  protected boolean isPlatformIndependent() {
    return false;
  }

  /**
   * List of flavors starting from platform-independent, so venv flavor goes before unix or windows flavor.
   * That could be used to find the first flavor that is {@link PythonSdkFlavor#isValidSdkPath(Path)} for example
   */
  @ApiStatus.Internal
  public static @NotNull List<PythonSdkFlavor<?>> getApplicableFlavors(boolean addPlatformIndependent) {
    List<PythonSdkFlavor<?>> result = new ArrayList<>();
    for (PythonSdkFlavor<?> flavor : EP_NAME.getExtensionList()) {
      if (flavor.isApplicable() || (addPlatformIndependent && flavor.isPlatformIndependent())) {
        result.add(flavor);
      }
    }

    result.addAll(getPlatformFlavorsFromExtensions());

    // Sort flavors to make venv go before unix/windows, see method doc
    if (addPlatformIndependent) {
      result.sort((f1, f2) -> Boolean.compare(f2.isPlatformIndependent(), f1.isPlatformIndependent()));
    }
    return result;
  }

  private static @NotNull List<PythonSdkFlavor<?>> getPlatformFlavorsFromExtensions() {
    List<PythonSdkFlavor<?>> result = new ArrayList<>();
    for (PythonFlavorProvider provider : PythonFlavorProvider.EP_NAME.getExtensionList()) {
      PythonSdkFlavor<?> flavor = provider.getFlavor();
      result.add(flavor);
    }
    return result;
  }

  @ApiStatus.Internal
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
    return tryDetectFlavorByLocalPath(Path.of(sdkPath));
  }

  /**
   * Detects {@link PythonSdkFlavor} for a local python path
   */
  @ApiStatus.Internal
  @RequiresBackgroundThread(generateAssertion = false) //No warning yet as there are usages: to be fixed
  public static @Nullable PythonSdkFlavor<?> tryDetectFlavorByLocalPath(@NotNull Path pythonBinaryPath) {
    // Iterate over all flavors starting with platform-independent (like venv): see `getApplicableFlavors` doc.
    // Order is important as venv must have priority over unix/windows
    for (PythonSdkFlavor<?> flavor : getApplicableFlavors(true)) {
      if (flavor.isValidSdkPath(pythonBinaryPath)) {
        return flavor;
      }
    }
    return null;
  }

  /**
   * It only validates path for local target
   */
  @ApiStatus.Internal
  public boolean isValidSdkPath(@NotNull Path pythonBinaryPath) {
    return Files.exists(pythonBinaryPath) && Files.isExecutable(pythonBinaryPath);
  }

  /**
   * @deprecated use {@link com.intellij.python.sdk.backend.PythonInterpreterKt#getVersion(PythonInterpreter, Continuation)}
   * or {@link com.intellij.python.community.execService.python.ApiKt#validatePythonAndGetInfo(Path, Continuation)}
   */
  @Deprecated(forRemoval = true)
  //because of process output
  @RequiresBackgroundThread(generateAssertion = false)
  public static @Nullable String getVersionStringStatic(@Nullable String sdkHome) {
    if (sdkHome == null) {
      return null;
    }
    final String runDirectory = Path.of(sdkHome).getParent().toString();
    final ProcessOutput processOutput = PySdkUtil.getProcessOutput(runDirectory, new String[]{sdkHome, PYTHON_VERSION_ARG}, 10000);
    return getVersionStringFromOutput(processOutput);
  }

  /**
   * @deprecated use {@link com.intellij.python.sdk.backend.PythonInterpreterKt#getVersion(PythonInterpreter, Continuation)}
   * or {@link com.intellij.python.community.execService.python.ApiKt#validatePythonAndGetInfo(Path, Continuation)}
   */
  @Deprecated(forRemoval = true)
  @ApiStatus.Internal
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

  /**
   * @deprecated use {@link com.intellij.python.sdk.backend.PythonInterpreterKt#getVersion(PythonInterpreter, Continuation)}
   * or {@link com.intellij.python.community.execService.python.ApiKt#validatePythonAndGetInfo(Path, Continuation)}
   */
  @Deprecated(forRemoval = true)
  @ApiStatus.Internal
  public static @Nullable String getVersionStringFromOutput(@NotNull String output) {
    return VersionParserKt.getVersionStringFromOutput(output);
  }

  @ApiStatus.Internal
  public @NotNull Collection<String> getExtraDebugOptions() {
    return Collections.emptyList();
  }

  @ApiStatus.Internal
  public void initPythonPath(@NotNull GeneralCommandLine cmd, boolean passParentEnvs, @NotNull Collection<String> path) {
    initPythonPath(path, passParentEnvs, cmd.getEnvironment());
  }

  public abstract @NotNull String getName();

  /**
   * Unique flavor name to be stored in persistence storage. Do not change value not to break compatibility.
   */
  @ApiStatus.Internal
  public @NotNull String getUniqueId() {
    return getClass().getSimpleName();
  }

  /**
   * @deprecated use {@link com.intellij.python.sdk.backend.PythonInterpreterKt#getVersion(PythonInterpreter, Continuation)}
   * or {@link com.intellij.python.community.execService.python.ApiKt#validatePythonAndGetInfo(Path, Continuation)}
   */
  @Deprecated(forRemoval = true)
  public @NotNull LanguageLevel getLanguageLevel(@NotNull Sdk sdk) {
    return getLanguageLevelFromVersionStringStatic(sdk.getVersionString());
  }


  /**
   * @deprecated use {@link com.intellij.python.sdk.backend.PythonInterpreterKt#getVersion(PythonInterpreter, Continuation)}
   * or {@link com.intellij.python.community.execService.python.ApiKt#validatePythonAndGetInfo(Path, Continuation)}
   */
  @Deprecated(forRemoval = true)
  @ApiStatus.Internal
  public static @NotNull LanguageLevel getLanguageLevelFromVersionStringStatic(@Nullable String version) {
    if (version == null) {
      return LanguageLevel.getDefault();
    }
    var result = LanguageLevel.getLanguageLevelFromVersionStringStaticSafe(version);
    return (result == null) ? LanguageLevel.getDefault() : result;
  }

  public @NotNull Icon getIcon() {
    return PythonParserIcons.PythonFile;
  }

  @ApiStatus.Internal
  public void initPythonPath(@NotNull Collection<String> path, boolean passParentEnvs, @NotNull Map<String, String> env) {
    PythonEnvUtil.initPythonPath(env, passParentEnvs, path);
  }

  @ApiStatus.Internal
  public @Nullable VirtualFile getSdkPath(@NotNull VirtualFile path) {
    return path;
  }

  @ApiStatus.Internal
  public @Nullable CommandLinePatcher commandLinePatcher() {
    return null;
  }

  /**
   * Could be called intentionally if another component suppose that
   * there could be new data provided by a flavor.
   */
  @ApiStatus.Internal
  public void dropCaches() {
  }

  @ApiStatus.Internal
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
