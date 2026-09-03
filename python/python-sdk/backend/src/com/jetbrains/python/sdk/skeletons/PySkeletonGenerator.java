// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.skeletons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.InvalidSdkException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class is a wrapper around the "generator3" helper script.
 * The script generates a stub ".py" definition for a binary module.
 * The wrapper starts the script with the options that {@link #commandBuilder()} provides.
 * It also communicates with the running script and reads its results.
 * <p>
 * Example of a generator call:
 * <pre>
 * {@code
 * refresher.getGenerator()
 *     .commandBuilder()
 *     // customize command line options and environment
 *     .runGeneration(progressIndicator)
 * }
 * </pre>
 *
 * @see Builder
 */
public abstract class PySkeletonGenerator {
  private static final class Run {
    static final Logger LOG = Logger.getInstance(Run.class);
  }

  protected static final Logger LOG = Logger.getInstance(PySkeletonGenerator.class);
  protected static final String GENERATOR3 = "generator3/__main__.py";

  public static final @NonNls String STATE_MARKER_FILE = ".state.json";
  public static final @NonNls String BLACKLIST_FILE_NAME = ".blacklist";

  private static final Gson ourGson = new GsonBuilder().create();

  protected final @NotNull Sdk mySdk;
  protected final @Nullable String myCurrentFolder;
  protected final @NotNull Path mySkeletonsPath;

  /**
   * @param skeletonPath  path where skeletons should be generated
   * @param pySdk         SDK
   * @param currentFolder current folder (some flavors may search for binary files there) or null if unknown
   */
  // TODO get rid of skeletonPath and currentFolder parameters and configure generator explicitly with builder
  public PySkeletonGenerator(@NotNull Path skeletonPath, final @NotNull Sdk pySdk, final @Nullable String currentFolder) {
    mySkeletonsPath = skeletonPath;
    mySdk = pySdk;
    myCurrentFolder = currentFolder;
  }

  public abstract @NotNull Builder commandBuilder();

  protected @NotNull List<GenerationResult> runGeneration(@NotNull Builder builder, @Nullable ProgressIndicator indicator)
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
            switch (level) {
              case "debug" -> Run.LOG.debug(message);
              case "trace" -> Run.LOG.trace(message);
            }
          }
          else if (msgType.equals("generation_result")) {
            results.add(ourGson.fromJson(trimmed, GenerationResult.class));
          }
        }
      }

      @Override
      public void onStderrLine(@NotNull String line) {
        Run.LOG.debug(StringUtil.trimTrailing(line));
      }
    };

    final ProcessOutput output = builder.runProcessWithLineOutputListener(listener);
    if (output.getExitCode() != 0) {
      throw new InvalidSdkException(formatGeneratorFailureMessage(output));
    }
    return results;
  }

  /**
   * @return true if the binary module {@code name} is present for this SDK.
   */
  public abstract boolean exists(@NotNull String name);

  public final @NotNull Path getSkeletonsPath() {
    return mySkeletonsPath;
  }

  /**
   * Builder object serving as a facade for the command-line interface of the generator,
   * allowing to additionally customize how it's going to be launched and performing the
   * default initialization before the run.
   */
  public abstract class Builder {
    protected final List<String> myExtraSysPath = new ArrayList<>();
    protected final List<String> myExtraArgs = new ArrayList<>();
    protected String myWorkingDir;
    protected String myTargetModuleName;
    protected String myTargetModulePath;
    protected boolean myPrebuilt = false;

    protected Builder() {
    }

    public final @NotNull Builder extraSysPath(@NotNull List<String> roots) {
      myExtraSysPath.addAll(roots);
      return this;
    }

    public final @NotNull Builder extraArgs(@NotNull List<String> args) {
      myExtraArgs.addAll(args);
      return this;
    }

    public final @NotNull Builder extraArgs(String @NotNull ... args) {
      return extraArgs(Arrays.asList(args));
    }

    public final @NotNull Builder workingDir(@NotNull String path) {
      myWorkingDir = path;
      return this;
    }

    public final @NotNull Builder inPrebuildingMode() {
      myPrebuilt = true;
      return this;
    }

    public final @NotNull Builder targetModule(@NotNull String name, @Nullable String path) {
      myTargetModuleName = name;
      myTargetModulePath = path;
      return this;
    }

    public final @NotNull List<GenerationResult> runGeneration(@Nullable ProgressIndicator indicator)
      throws InvalidSdkException, ExecutionException {
      return PySkeletonGenerator.this.runGeneration(this, indicator);
    }

    public abstract @NotNull ProcessOutput runProcessWithLineOutputListener(@NotNull LineWiseProcessOutputListener listener)
      throws InvalidSdkException, ExecutionException;
  }

  protected final @NotNull @NlsSafe String formatGeneratorFailureMessage(@NotNull ProcessOutput process) {
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

  public final void refreshGeneratedSkeletons() {
    VirtualFile skeletonsVFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(getSkeletonsPath());
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
  public static final class GenerationResult {
    @SerializedName("module_name")
    private String myModuleName;
    @SerializedName("module_origin")
    private String myModuleOrigin;
    @SerializedName("generation_status")
    private GenerationStatus myGenerationStatus;

    public @NotNull String getModuleName() {
      return myModuleName;
    }

    public @NotNull String getModuleOrigin() {
      return myModuleOrigin;
    }

    public @NotNull GenerationStatus getGenerationStatus() {
      return myGenerationStatus;
    }

    public boolean isBuiltin() {
      return myModuleOrigin.equals("(built-in)");
    }
  }
}
