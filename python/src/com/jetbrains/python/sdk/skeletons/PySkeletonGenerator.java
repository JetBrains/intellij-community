// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.skeletons;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.IronPythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.jetbrains.python.sdk.skeleton.PySkeletonHeader.fromVersionString;

/**
 * @author traff
 */
public class PySkeletonGenerator {
  private static final Gson ourGson = new GsonBuilder().create();

  // Some flavors need current folder to be passed as param. Here are they.
  private static final Map<Class<? extends PythonSdkFlavor>, String> ENV_PATH_PARAM =
    new HashMap<>();

  static {
    ENV_PATH_PARAM.put(IronPythonSdkFlavor.class, "IRONPYTHONPATH"); // TODO: Make strategy and move to PythonSdkFlavor?
  }

  protected static final Logger LOG = Logger.getInstance(PySkeletonGenerator.class);
  protected static final int MINUTE = 60 * 1000;
  protected static final String GENERATOR3 = "generator3/__main__.py";

  private final Sdk mySdk;
  @Nullable private String myCurrentFolder;
  private String mySkeletonsPath;
  @NotNull protected final Map<String, String> myEnv;

  private boolean myPrebuilt = false;
  private Map<String, String> myExtraEnv;
  private List<String> myExtraSysPath;
  private List<String> myAssemblyRefs;
  private List<String> myExtraArgs;
  private String myTargetModuleName;
  private String myTargetModulePath;

  @NotNull
  public PySkeletonGenerator withExtraEnvironment(@NotNull Map<String, String> environment) {
    myExtraEnv = environment;
    return this;
  }

  @NotNull
  public PySkeletonGenerator withExtraSysPath(@NotNull List<String> roots) {
    myExtraSysPath = roots;
    return this;
  }

  @NotNull
  public PySkeletonGenerator withAssemblyRefs(@NotNull List<String> assemblyRefs) {
    myAssemblyRefs = assemblyRefs;
    return this;
  }

  @NotNull
  public PySkeletonGenerator withExtraArgs(@NotNull List<String> args) {
    myExtraArgs = args;
    return this;
  }

  @NotNull
  public PySkeletonGenerator withSkeletonsDir(@NotNull String path) {
    mySkeletonsPath = path;
    return this;
  }

  @NotNull
  public PySkeletonGenerator withWorkingDir(@NotNull String path) {
    myCurrentFolder = path;
    return this;
  }

  @NotNull
  public PySkeletonGenerator inPrebuildingMode() {
    myPrebuilt = true;
    return this;
  }

  @NotNull
  public PySkeletonGenerator withTargetModule(@NotNull String name, @Nullable String path) {
    myTargetModuleName = name;
    myTargetModulePath = path;
    return this;
  }

  @NotNull
  public ProcessOutput runProcess() throws ExecutionException, InvalidSdkException {
    return getProcessOutput(getWorkingDir(),
                            ArrayUtil.toStringArray(buildCommandLine()),
                            buildEnvironment(),
                            MINUTE * 10);
  }

  @NotNull
  public List<GenerationResult> runGeneration(@Nullable ProgressIndicator indicator) throws ExecutionException, InvalidSdkException {

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
              if (controlMessage.get("minor").getAsBoolean()) {
                indicator.setText2(text.getAsString());
              }
              else {
                indicator.setText(text.getAsString());
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
              LOG.info(message);
            }
            else if (level.equals("debug")) {
              LOG.debug(message);
            }
            else if (level.equals("trace")) {
              LOG.trace(message);
            }
          }
          else if (msgType.equals("generation_result")) {
            results.add(ourGson.fromJson(trimmed, GenerationResult.class));
          }
        }
      }

      @Override
      public void onStderrLine(@NotNull String line) {
        LOG.info(StringUtil.trimTrailing(line));
      }
    };

    runProcessWithLineOutputListener(getWorkingDir(), buildCommandLine(), buildEnvironment(), MINUTE * 20, listener);
    return results;
  }

  @NotNull
  private String getWorkingDir() throws InvalidSdkException {
    if (myCurrentFolder != null) {
      return myCurrentFolder;
    }
    final String binaryPath = mySdk.getHomePath();
    if (binaryPath == null) throw new InvalidSdkException("Broken home path for " + mySdk.getName());
    return new File(binaryPath).getParent();
  }


  @NotNull
  protected List<String> buildCommandLine() {
    final List<String> commandLine = new ArrayList<>();
    commandLine.add(mySdk.getHomePath());
    commandLine.add(PythonHelpersLocator.getHelperPath(GENERATOR3));
    commandLine.add("-d");
    commandLine.add(mySkeletonsPath);
    if (!ContainerUtil.isEmpty(myAssemblyRefs)) {
      commandLine.add("-c");
      commandLine.add(StringUtil.join(myAssemblyRefs, ";"));
    }
    if (ApplicationManager.getApplication().isInternal()) {
      commandLine.add("-x");
    }
    if (!ContainerUtil.isEmpty(myExtraSysPath)) {
      commandLine.add("-s");
      commandLine.add(StringUtil.join(myExtraSysPath, File.pathSeparator));
    }
    if (!ContainerUtil.isEmpty(myExtraArgs)) {
      commandLine.addAll(myExtraArgs);
    }
    if (StringUtil.isNotEmpty(myTargetModuleName)) {
      commandLine.add(myTargetModuleName);
      if (StringUtil.isNotEmpty(myTargetModulePath)) {
        commandLine.add(myTargetModulePath);
      }
    }
    return commandLine;
  }

  @NotNull
  protected Map<String, String> buildEnvironment() {
    Map<String, String> env = ImmutableMap.of("PYTHONPATH", PythonHelpersLocator.getHelpersRoot().getPath());
    //final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(mySdk);
    //if (myCurrentFolder != null && flavor != null && ENV_PATH_PARAM.containsKey(flavor.getClass())) {
    //  final Map<String, String> interpreterExtraEnv = ImmutableMap.of(ENV_PATH_PARAM.get(flavor.getClass()), myCurrentFolder);
    //  env = PySdkUtil.mergeEnvVariables(env, interpreterExtraEnv);
    //}
    if (myExtraEnv != null) {
      env = PySdkUtil.mergeEnvVariables(env, myExtraEnv);
    }
    env = PySdkUtil.mergeEnvVariables(env, PythonSdkType.activateVirtualEnv(mySdk));
    PythonEnvUtil.setPythonDontWriteBytecode(env);
    if (myPrebuilt) {
      env.put("IS_PREGENERATED_SKELETONS", "1");
    }
    return env;
  }

  public void finishSkeletonsGeneration() {
  }

  public boolean exists(@NotNull final String name) {
    return new File(name).exists();
  }

  public void setPrebuilt(boolean prebuilt) {
    myPrebuilt = prebuilt;
  }

  /**
   * @param skeletonPath path where skeletons should be generated
   * @param pySdk SDK
   * @param currentFolder current folder (some flavors may search for binary files there) or null if unknown
   */
  public PySkeletonGenerator(String skeletonPath, @NotNull final Sdk pySdk, @Nullable final String currentFolder) {
    mySkeletonsPath = skeletonPath;
    mySdk = pySdk;
    myCurrentFolder = currentFolder;
    Map<String, String> env = ImmutableMap.of("PYTHONPATH", PythonHelpersLocator.getHelpersRoot().getPath());

    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(pySdk);
    if (currentFolder != null && flavor != null && ENV_PATH_PARAM.containsKey(flavor.getClass())) {
      final Map<String, String> interpreterExtraEnv = ImmutableMap.of(ENV_PATH_PARAM.get(flavor.getClass()), currentFolder);
      env = PySdkUtil.mergeEnvVariables(env, interpreterExtraEnv);
    }
    myEnv = env;
  }

  protected void runProcessWithLineOutputListener(@NotNull String homePath,
                                                  @NotNull List<String> cmd,
                                                  @NotNull Map<String, String> env,
                                                  int timeout,
                                                  @NotNull LineWiseProcessOutputListener listener) throws ExecutionException {
    final GeneralCommandLine commandLine = new GeneralCommandLine(cmd)
      .withWorkDirectory(homePath)
      .withEnvironment(env);
    final CapturingProcessHandler handler = new CapturingProcessHandler(commandLine);
    handler.addProcessListener(new LineWiseProcessOutputListenerAdapter(listener));
    handler.runProcess(timeout);
  }

  public String getSkeletonsPath() {
    return mySkeletonsPath;
  }

  public void prepare() {
  }

  protected void generateSkeleton(String modname,
                                  String modfilename,
                                  List<String> assemblyRefs,
                                  String syspath,
                                  Sdk sdk,
                                  Consumer<Boolean> resultConsumer)
    throws InvalidSdkException {

    final ProcessOutput genResult = runSkeletonGeneration(modname, modfilename, assemblyRefs, sdk, syspath);

    final Application app = ApplicationManager.getApplication();
    if (app.isInternal() || app.isEAP()) {
      final String stdout = genResult.getStdout();
      if (StringUtil.isNotEmpty(stdout)) {
        LOG.info(stdout);
      }
    }
    if (!genResult.getStderrLines().isEmpty()) {
      StringBuilder sb = new StringBuilder("Skeleton for ");
      sb.append(modname);
      if (genResult.getExitCode() != 0) {
        sb.append(" failed on ");
      }
      else {
        sb.append(" had some minor errors on ");
      }
      sb.append(sdk.getHomePath()).append(". stderr: --\n");
      for (String err_line : genResult.getStderrLines()) {
        sb.append(err_line).append("\n");
      }
      sb.append("--");
      if (app.isInternal()) {
        LOG.warn(sb.toString());
      }
      else {
        LOG.info(sb.toString());
      }
    }

    resultConsumer.consume(genResult.getExitCode() == 0);
  }

  public ProcessOutput runSkeletonGeneration(String modname,
                                             String modfilename,
                                             List<String> assemblyRefs,
                                             Sdk sdk, String extraSyspath)
    throws InvalidSdkException {
    final String binaryPath = sdk.getHomePath();
    final String parent_dir = new File(binaryPath).getParent();
    final List<String> commandLine = buildSkeletonGeneratorCommandLine(modname, modfilename, assemblyRefs, binaryPath, extraSyspath);
    return getProcessOutput(parent_dir, ArrayUtilRt.toStringArray(commandLine), PythonSdkType.activateVirtualEnv(sdk), MINUTE * 10);
  }

  @NotNull
  protected final List<String> buildSkeletonGeneratorCommandLine(@NotNull String modname,
                                                                 @Nullable String modfilename,
                                                                 @Nullable List<String> assemblyRefs,
                                                                 @NotNull String binaryPath,
                                                                 @Nullable String extraSyspath) {
    List<String> commandLine = new ArrayList<>();
    commandLine.add(binaryPath);
    commandLine.add(PythonHelpersLocator.getHelperPath(GENERATOR3));
    commandLine.add("-d");
    commandLine.add(getSkeletonsPath());
    if (assemblyRefs != null && !assemblyRefs.isEmpty()) {
      commandLine.add("-c");
      commandLine.add(StringUtil.join(assemblyRefs, ";"));
    }
    if (ApplicationManager.getApplication().isInternal()) {
      commandLine.add("-x");
    }
    if (!StringUtil.isEmpty(extraSyspath)) {
      commandLine.add("-s");
      commandLine.add(extraSyspath);
    }
    commandLine.add(modname);
    if (modfilename != null) {
      commandLine.add(modfilename);
    }
    return commandLine;
  }

  protected ProcessOutput getProcessOutput(String homePath, @NotNull String[] commandLine, Map<String, String> extraEnv,
                                           int timeout) throws InvalidSdkException {
    final Map<String, String> env = PySdkUtil.mergeEnvVariables(myEnv, extraEnv);
    PythonEnvUtil.setPythonDontWriteBytecode(env);
    if (myPrebuilt) {
      env.put("IS_PREGENERATED_SKELETONS", "1");
    }
    return PySdkUtil.getProcessOutput(homePath, commandLine, env, timeout);
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

  private static class LineWiseProcessOutputListenerAdapter extends ProcessAdapter {
    private final StringBuilder myStdoutLine = new StringBuilder();
    private final StringBuilder myStderrLine = new StringBuilder();
    private final LineWiseProcessOutputListener myListener;

    private LineWiseProcessOutputListenerAdapter(@NotNull LineWiseProcessOutputListener listener) {
      myListener = listener;
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
      final boolean isStdout = ProcessOutputType.isStdout(outputType);
      final StringBuilder lineBuilder = isStdout ? myStdoutLine : myStderrLine;
      for (String chunk : StringUtil.splitByLinesKeepSeparators(event.getText())) {
        lineBuilder.append(chunk);
        if (StringUtil.isLineBreak(lineBuilder.charAt(lineBuilder.length() - 1))) {
          final String line = lineBuilder.toString();
          if (isStdout) {
            myListener.onStdoutLine(line);
          }
          else {
            myListener.onStderrLine(line);
          }
          lineBuilder.setLength(0);
        }
      }
    }

    @Override
    public void processTerminated(@NotNull ProcessEvent event) {
      if (myStdoutLine.length() != 0) {
        myListener.onStdoutLine(myStdoutLine.toString());
      }
      if (myStderrLine.length() != 0) {
        myListener.onStderrLine(myStderrLine.toString());
      }
    }
  }

  public enum GenerationStatus {
    UP_TO_DATE,
    GENERATED,
    COPIED,
    FAILED,
  }

  public static class GenerationResult {
    @SerializedName("module_name")
    public String myModuleName;
    @SerializedName("module_origin")
    public String myModuleOrigin;
    @SerializedName("generation_status")
    public GenerationStatus myGenerationStatus;

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

  public interface LineWiseProcessOutputListener {
    void onStdoutLine(@NotNull String line);

    default void onStderrLine(@NotNull String line) {}
  }
}
