package com.jetbrains.python.sdk.skeletons;

import com.google.common.collect.ImmutableMap;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Time;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PyLegacySkeletonGenerator extends PySkeletonGenerator {
  /**
   * @param skeletonPath  path where skeletons should be generated
   * @param pySdk         SDK
   * @param currentFolder current folder (some flavors may search for binary files there) or null if unknown
   */
  public PyLegacySkeletonGenerator(@NotNull String skeletonPath,
                                   @NotNull Sdk pySdk,
                                   @Nullable String currentFolder) {
    super(skeletonPath, pySdk, currentFolder);
  }

  @Override
  @NotNull
  public final Builder commandBuilder() {
    final Builder builder = new LegacyBuilder();
    if (myCurrentFolder != null) {
      builder.workingDir(myCurrentFolder);
    }
    return builder;
  }

  /**
   * @param ensureSuccess throw {@link InvalidSdkException} containing additional diagnostic on process non-zero exit code.
   *                      You might want to disable it for commands where non-zero exit code is possible for situations other
   *                      than misconfigured interpreter or execution error in order to inspect the output manually.
   */
  @NotNull
  protected ProcessOutput runProcess(@NotNull Builder builder, boolean ensureSuccess) throws InvalidSdkException {
    ProcessOutput output = builder.runProcess();
    if (ensureSuccess && output.getExitCode() != 0) {
      throw new InvalidSdkException(formatGeneratorFailureMessage(output));
    }
    return output;
  }

  // [targets-api] this should be left as-is because it deals with non-structured `commandLine`
  protected @NotNull ProcessOutput getProcessOutput(@Nullable String homePath,
                                                    String @NotNull [] commandLine,
                                                    @Nullable String stdin,
                                                    @Nullable Map<String, String> extraEnv,
                                                    int timeout) throws InvalidSdkException {
    final byte[] bytes = stdin != null ? stdin.getBytes(StandardCharsets.UTF_8) : null;
    return PySdkUtil.getProcessOutput(homePath, commandLine, extraEnv, timeout, bytes, true);
  }

  @NotNull
  protected ProcessOutput runProcessWithLineOutputListener(@NotNull String homePath,
                                                           @NotNull List<String> cmd,
                                                           @NotNull Map<String, String> env,
                                                           @Nullable String stdin,
                                                           int timeout,
                                                           @NotNull LineWiseProcessOutputListener listener)
    throws ExecutionException, InvalidSdkException {
    final GeneralCommandLine commandLine = new GeneralCommandLine(cmd)
      .withWorkDirectory(homePath)
      .withEnvironment(env);
    final CapturingProcessHandler handler = new CapturingProcessHandler(commandLine);
    if (stdin != null) {
      sendLineToProcessInput(handler, stdin);
    }
    handler.addProcessListener(new LineWiseProcessOutputListener.Adapter(listener));
    return handler.runProcess(timeout);
  }

  private final class LegacyBuilder extends Builder {

    @NotNull
    public List<String> getCommandLine() {
      final List<String> commandLine = new ArrayList<>();
      commandLine.add(mySdk.getHomePath());
      commandLine.add(PythonHelpersLocator.getHelperPath(GENERATOR3));
      commandLine.add("-d");
      commandLine.add(mySkeletonsPath);
      if (!ContainerUtil.isEmpty(myAssemblyRefs)) {
        commandLine.add("-c");
        commandLine.add(StringUtil.join(myAssemblyRefs, ";"));
      }
      if (!ContainerUtil.isEmpty(myExtraSysPath)) {
        commandLine.add("-s");
        commandLine.add(StringUtil.join(myExtraSysPath, File.pathSeparator));
      }
      commandLine.addAll(myExtraArgs);
      if (StringUtil.isNotEmpty(myTargetModuleName)) {
        commandLine.add(myTargetModuleName);
        if (StringUtil.isNotEmpty(myTargetModulePath)) {
          commandLine.add(myTargetModulePath);
        }
      }
      return commandLine;
    }

    @NotNull
    public Map<String, String> getEnvironment() {
      Map<String, String> env = new HashMap<>();
      final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(mySdk);
      final String flavorPathParam = flavor != null ? flavor.envPathParam() : null;
      // TODO Investigate whether it's possible to pass this directory as an ordinary "extraSysPath" entry
      if (myWorkingDir != null && flavorPathParam != null) {
        env = PySdkUtil.mergeEnvVariables(env, ImmutableMap.of(flavorPathParam, myWorkingDir));
      }
      env = PySdkUtil.mergeEnvVariables(env, PySdkUtil.activateVirtualEnv(mySdk));
      PythonEnvUtil.setPythonDontWriteBytecode(env);
      if (myPrebuilt) {
        env.put("IS_PREGENERATED_SKELETONS", "1");
      }
      return env;
    }

    @NotNull
    public String getWorkingDir() throws InvalidSdkException {
      if (myWorkingDir != null) {
        return myWorkingDir;
      }
      final String binaryPath = mySdk.getHomePath();
      if (binaryPath == null) throw new InvalidSdkException(PySdkBundle.message("dialog.message.broken.home.path.for", mySdk.getName()));
      return new File(binaryPath).getParent();
    }

    @Override
    @NotNull
    public ProcessOutput runProcess() throws InvalidSdkException {
      return getProcessOutput(getWorkingDir(),
                              ArrayUtil.toStringArray(getCommandLine()),
                              getStdin(),
                              getEnvironment(),
                              getTimeout(Time.MINUTE * 10));
    }

    @Override
    public @NotNull ProcessOutput runProcessWithLineOutputListener(@NotNull LineWiseProcessOutputListener listener)
      throws InvalidSdkException, ExecutionException {
      return PyLegacySkeletonGenerator.this.runProcessWithLineOutputListener(getWorkingDir(),
                                                                             getCommandLine(),
                                                                             getEnvironment(),
                                                                             myStdin,
                                                                             getTimeout(Time.MINUTE * 20),
                                                                             listener);
    }
  }
}
