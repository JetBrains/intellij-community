// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.skeletons;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.BaseProcessHandler;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Time;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This class serves two purposes. First, it's a wrapper around "generator3" helper script
 * that is used to generate stub ".py" definitions for binary modules. The wrapper helps to
 * launch it using multitude of existing options (see {@link #commandBuilder()}), communicate
 * with a running generator instance and interpret its results. Second, it's an extension
 * necessary to customize generation steps for various interpreter flavors supported in PyCharm,
 * first of all, remote ones (see {@link com.jetbrains.python.remote.PyRemoteSkeletonGeneratorFactory} EP).
 * <p>
 * Conceptually there are two distinct modes for launching the generator:
 * <ul>
 * <li>In the main mode it actually produces stubs for binaries either explicitly specified or
 * automatically discovered in {@code sys.path}. As generation goes it communicates with
 * the IDE in JSON chunks containing progress indication, log messages and intermediate results, e.g.
 * <p>{@code {"type": "progress", "text": "_hashlib", "fraction": 0.2, "minor": true}}</p>
 * or
 * <p>{"type": "generation_result", "module_origin": "/usr/lib/python2.7/lib-dynload/_hashlib.so", "module_name": "_hashlib", "generation_status": "GENERATED"}</p>
 * <p>
 * To fully support this mode with real-time progress indication all inheritors must support
 * reading process' stdout/stderr interactively line by line by implementing
 * {@link #runProcessWithLineOutputListener(String, List, Map, String, int, LineWiseProcessOutputListener)}.
 * The provided {@link LineWiseProcessOutputListener} will handle all the service lines written to stdout.
 * <p>
 * Example of generator invocation in the main mode:
 * <pre>
 * {@code
 * new PySkeletonGenerator(skeletonsPath, sdk, workingDir)
 *     .commandBuilder()
 *     // customize command line options and environment
 *     .runGeneration(progressIndicator)
 * }
 * </pre>
 * </li>
 * <li>
 * The second mode is intended for all the other commands supported by the script but not directly related to generation, e.g.
 * listing source files found in {@code sys.path} and zipping them in an archive (which is used by some remote interpreters).
 * These commands normally don't use any special service commands and don't have intermediate results, therefore one can
 * simply run them as:
 * <pre>
 * {@code
 * new PySkeletonGenerator(skeletonsPath, sdk, workingDir)
 *     .commandBuilder()
 *     // customize command line options and environment
 *     .runProcess()
 * }
 * and manually interpret process' output and exit code as needed.
 * </pre>
 * </li>
 *
 * @see Builder
 */
public class PySkeletonGenerator {
  private static class Run {
    static final Logger LOG = Logger.getInstance(Run.class);
  }

  protected static final Logger LOG = Logger.getInstance(PySkeletonGenerator.class);
  protected static final String GENERATOR3 = "generator3/__main__.py";

  @NonNls public static final String STATE_MARKER_FILE = ".state.json";
  @NonNls public static final String BLACKLIST_FILE_NAME = ".blacklist";

  private static final Gson ourGson = new GsonBuilder().create();

  protected final Sdk mySdk;
  @Nullable private final String myCurrentFolder;
  private final String mySkeletonsPath;

  /**
   * @param skeletonPath  path where skeletons should be generated
   * @param pySdk         SDK
   * @param currentFolder current folder (some flavors may search for binary files there) or null if unknown
   */
  // TODO get rid of skeletonPath and currentFolder parameters and configure generator explicitly with builder
  public PySkeletonGenerator(String skeletonPath, @NotNull final Sdk pySdk, @Nullable final String currentFolder) {
    mySkeletonsPath = skeletonPath;
    mySdk = pySdk;
    myCurrentFolder = currentFolder;
  }

  @NotNull
  public final Builder commandBuilder() {
    final Builder builder = new Builder();
    if (myCurrentFolder != null) {
      builder.workingDir(myCurrentFolder);
    }
    return builder;
  }


  /**
   * Builder object serving as a facade for the command-line interface of the generator,
   * allowing to additionally customize how it's going to be launched and performing the
   * default initialization before the run.
   */
  public final class Builder {
    private final List<String> myExtraSysPath = new ArrayList<>();
    private final List<String> myAssemblyRefs = new ArrayList<>();
    private final List<String> myExtraArgs = new ArrayList<>();
    private String myWorkingDir;
    private String myTargetModuleName;
    private String myTargetModulePath;
    private boolean myPrebuilt = false;
    private int myTimeout;
    private String myStdin;

    private Builder() {
    }

    @NotNull
    public Builder extraSysPath(@NotNull List<String> roots) {
      myExtraSysPath.addAll(roots);
      return this;
    }

    @NotNull
    public Builder assemblyRefs(@NotNull List<String> assemblyRefs) {
      myAssemblyRefs.addAll(assemblyRefs);
      return this;
    }

    @NotNull
    public Builder extraArgs(@NotNull List<String> args) {
      myExtraArgs.addAll(args);
      return this;
    }

    @NotNull
    public Builder extraArgs(String @NotNull ... args) {
      return extraArgs(Arrays.asList(args));
    }

    @NotNull
    public Builder workingDir(@NotNull String path) {
      myWorkingDir = path;
      return this;
    }

    @NotNull
    public Builder inPrebuildingMode() {
      myPrebuilt = true;
      return this;
    }

    @NotNull
    public Builder targetModule(@NotNull String name, @Nullable String path) {
      myTargetModuleName = name;
      myTargetModulePath = path;
      return this;
    }

    @NotNull
    public Builder timeout(int timeout) {
      myTimeout = timeout;
      return this;
    }

    @NotNull
    public Builder stdin(@NotNull String content) {
      myStdin = content;
      return this;
    }

    @Nullable
    public String getStdin() {
      return myStdin;
    }

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
      if (binaryPath == null) {
        throw new InvalidSdkException(PySdkBundle.message("python.skeleton.generator.broken.home.path", mySdk.getName()));
      }
      return new File(binaryPath).getParent();
    }

    public int getTimeout(int defaultTimeout) {
      return myTimeout > 0 ? myTimeout : defaultTimeout;
    }

    /**
     * @param ensureSuccess throw {@link InvalidSdkException} containing additional diagnostic on process non-zero exit code.
     *                      You might want to disable it for commands where non-zero exit code is possible for situations other
     *                      than misconfigured interpreter or execution error in order to inspect the output manually.
     */
    @NotNull
    public ProcessOutput runProcess(boolean ensureSuccess) throws InvalidSdkException {
      return PySkeletonGenerator.this.runProcess(this, ensureSuccess);
    }

    @NotNull
    public List<GenerationResult> runGeneration(@Nullable ProgressIndicator indicator) throws InvalidSdkException, ExecutionException {
      return PySkeletonGenerator.this.runGeneration(this, indicator);
    }
  }

  @NotNull
  protected List<GenerationResult> runGeneration(@NotNull Builder builder, @Nullable ProgressIndicator indicator)
    throws InvalidSdkException, ExecutionException {
    final List<GenerationResult> results = new ArrayList<>();
    final LineWiseProcessOutputListener listener = new LineWiseProcessOutputListener() {
      @Override
      public void onStdoutLine(@NotNull String line) {
        if (indicator != null) {
          indicator.checkCanceled();
        }
        final String trimmed = line.trim();
        if (trimmed.startsWith("{")) {
          final JsonObject controlMessage;
          try {
            controlMessage = ourGson.fromJson(trimmed, JsonObject.class);
          }
          catch (JsonSyntaxException e) {
            LOG.warn("Malformed control message: " + line);
            return;
          }
          final String msgType = controlMessage.get("type").getAsString();
          if (msgType.equals("progress") && indicator != null) {
            final JsonElement text = controlMessage.get("text");
            if (text != null) {
              final @NlsSafe String progressText = text.getAsString();
              if (controlMessage.get("minor").getAsBoolean()) {
                indicator.setText2(progressText);
              }
              else {
                indicator.setText(progressText);
              }
            }
            final JsonElement fraction = controlMessage.get("fraction");
            if (fraction != null) {
              indicator.setIndeterminate(false);
              indicator.setFraction(fraction.getAsDouble());
            }
          }
          else if (msgType.equals("log")) {
            final String level = controlMessage.get("level").getAsString();
            final String message = controlMessage.get("message").getAsString();
            if (level.equals("info")) {
              Run.LOG.info(message);
            }
            else if (level.equals("debug")) {
              Run.LOG.debug(message);
            }
            else if (level.equals("trace")) {
              Run.LOG.trace(message);
            }
          }
          else if (msgType.equals("generation_result")) {
            results.add(ourGson.fromJson(trimmed, GenerationResult.class));
          }
        }
      }

      @Override
      public void onStderrLine(@NotNull String line) {
        Run.LOG.info(StringUtil.trimTrailing(line));
      }
    };

    final ProcessOutput output = runProcessWithLineOutputListener(builder.getWorkingDir(),
                                                                  builder.getCommandLine(),
                                                                  builder.getEnvironment(),
                                                                  builder.myStdin,
                                                                  builder.getTimeout(Time.MINUTE * 20),
                                                                  listener);
    if (output.getExitCode() != 0) {
      throw new InvalidSdkException(formatGeneratorFailureMessage(output));
    }
    return results;
  }

  @NotNull
  protected ProcessOutput runProcess(@NotNull Builder builder, boolean ensureSuccess) throws InvalidSdkException {
    final ProcessOutput output = getProcessOutput(builder.getWorkingDir(),
                                                  ArrayUtil.toStringArray(builder.getCommandLine()),
                                                  builder.getStdin(),
                                                  builder.getEnvironment(),
                                                  builder.getTimeout(Time.MINUTE * 10));
    if (ensureSuccess && output.getExitCode() != 0) {
      throw new InvalidSdkException(formatGeneratorFailureMessage(output));
    }
    return output;
  }

  public void finishSkeletonsGeneration() {
  }

  public boolean exists(@NotNull final String name) {
    return new File(name).exists();
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

  public String getSkeletonsPath() {
    return mySkeletonsPath;
  }

  public void prepare() {
  }

  protected ProcessOutput getProcessOutput(String homePath,
                                           String @NotNull [] commandLine,
                                           @Nullable String stdin,
                                           Map<String, String> extraEnv,
                                           int timeout) throws InvalidSdkException {
    final byte[] bytes = stdin != null ? stdin.getBytes(StandardCharsets.UTF_8) : null;
    return PySdkUtil.getProcessOutput(homePath, commandLine, extraEnv, timeout, bytes, true);
  }

  @NotNull
  private @NlsSafe String formatGeneratorFailureMessage(@NotNull ProcessOutput process) {
    final StringBuilder sb = new StringBuilder("failed to run ").append(GENERATOR3).append(" for ").append(mySdk.getHomePath());
    if (process.isTimeout()) {
      sb.append(": timed out.");
    }
    else {
      sb.append(", exit code ")
        .append(process.getExitCode())
        .append(", stderr: \n-----\n");
      for (String line : process.getStderrLines()) {
        sb.append(line).append("\n");
      }
      sb.append("-----");
    }
    return sb.toString();
  }

  public boolean deleteOrLog(@NotNull File item) {
    boolean deleted = item.delete();
    if (!deleted) LOG.warn("Failed to delete skeleton file " + item.getAbsolutePath());
    return deleted;
  }

  public void refreshGeneratedSkeletons() {
    VirtualFile skeletonsVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(getSkeletonsPath());
    assert skeletonsVFile != null;
    skeletonsVFile.refresh(false, true);
  }

  public enum GenerationStatus {
    UP_TO_DATE,
    GENERATED,
    COPIED,
    FAILED,
  }

  @SuppressWarnings("unused")
  public static class GenerationResult {
    @SerializedName("module_name")
    private String myModuleName;
    @SerializedName("module_origin")
    private String myModuleOrigin;
    @SerializedName("generation_status")
    private GenerationStatus myGenerationStatus;

    @NotNull
    public String getModuleName() {
      return myModuleName;
    }

    @NotNull
    public String getModuleOrigin() {
      return myModuleOrigin;
    }

    @NotNull
    public GenerationStatus getGenerationStatus() {
      return myGenerationStatus;
    }

    public boolean isBuiltin() {
      return myModuleOrigin.equals("(built-in)");
    }
  }

  protected static void sendLineToProcessInput(@NotNull BaseProcessHandler<?> handler, @NotNull String line) throws ExecutionException {
    final OutputStream input = handler.getProcessInput();
    if (input != null) {
      try {
        sendLineToStream(input, line);
      }
      catch (IOException e) {
        throw new ExecutionException(e);
      }
    }
    else {
      LOG.warn("Process " + handler.getCommandLine() + " can't accept any input");
    }
  }

  protected static void sendLineToStream(@NotNull OutputStream input, @NotNull String line) throws IOException {
    // Using platform-specific notion of a line separator (PrintStream, PrintWriter, BufferedWriter#newLine()) is
    // unreliable in case of remote interpreters where target and host OS might differ.

    // Closing the underlying input stream should be handled by its owner.
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") final BufferedWriter writer =
      new BufferedWriter(new OutputStreamWriter(input, StandardCharsets.UTF_8));
    writer.write(line);
    writer.write('\n');
    writer.flush();
  }
}
