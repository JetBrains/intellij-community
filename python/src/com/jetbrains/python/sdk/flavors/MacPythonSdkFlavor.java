// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.PyDetectedSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.openapi.util.text.HtmlChunk.raw;
import static com.intellij.openapi.util.text.HtmlChunk.text;


public final class MacPythonSdkFlavor extends CPythonSdkFlavor<PyFlavorData.Empty> {

  private static final Logger LOGGER = Logger.getInstance(MacPythonSdkFlavor.class);

  private MacPythonSdkFlavor() {
  }

  @Override
  public boolean isApplicable() {
    return SystemInfo.isMac;
  }

  @Override
  public @NotNull Class<PyFlavorData.Empty> getFlavorDataClass() {
    return PyFlavorData.Empty.class;
  }

  @RequiresBackgroundThread
  @Override
  protected @NotNull Collection<@NotNull Path> suggestLocalHomePathsImpl(@Nullable Module module, @Nullable UserDataHolder context) {
    Set<Path> candidates = new HashSet<>();
    collectPythonInstallations(Path.of("/Library/Frameworks/Python.framework/Versions"), candidates);
    collectPythonInstallations(Path.of("/System/Library/Frameworks/Python.framework/Versions"), candidates);
    collectPythonInstallations(Path.of("/usr/local/Cellar/python"), candidates);
    UnixPythonSdkFlavor.collectUnixPythons(Path.of("/usr/local/bin"), candidates);
    if (areCommandLineDeveloperToolsAvailable()) {
      UnixPythonSdkFlavor.collectUnixPythons(Path.of("/usr/bin"), candidates);
      UnixPythonSdkFlavor.collectPyenvPythons(candidates);
    }

    return candidates;
  }

  private static void collectPythonInstallations(@NotNull Path pythonHomePath, @NotNull Set<Path> candidates) {
    Path pythonBinaryPath = pythonHomePath.resolve(Path.of("bin", "python3"));
    if (Files.isRegularFile(pythonBinaryPath)) {
      candidates.add(pythonBinaryPath);
    }
  }

  public static @NotNull GeneralCommandLine getXCodeSelectInstallCommand() {
    return new GeneralCommandLine("xcode-select", "--install");
  }

  private static @NotNull GeneralCommandLine getXCodeSelectPathCommand() {
    return new GeneralCommandLine("xcode-select", "-p");
  }

  /**
   * This method is used to check whether {@code /usr/bin/python3} is a real interpreter or a fake binary
   * which execution leads to a dialog with dev tools installation.
   *
   * @return true if dev tools are installed and {@code /usr/bin/python3} can be used.
   */
  public static boolean areCommandLineDeveloperToolsAvailable() {
    final GeneralCommandLine commandLine = getXCodeSelectPathCommand();

    try {
      final ProcessOutput output = ExecUtil.execAndGetOutput(commandLine);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Result of '" + commandLine.getCommandLineString() + "':\n" + output);
      }

      return output.getExitCode() == 0;
    }
    catch (ExecutionException e) {
      LOGGER.warn("Exception during '" + commandLine.getCommandLineString() + "'", e);
      return true;
    }
  }

  public static @Nullable ValidationInfo checkDetectedPython(@NotNull PyDetectedSdk sdk) {
    if (!"/usr/bin/python3".equals(sdk.getHomePath())) return null;

    //noinspection DialogTitleCapitalization
    final String progressTitle = PyBundle.message("python.cldt.checking");

    if (ProgressManager
      .getInstance()
      .runProcessWithProgressSynchronously(
        MacPythonSdkFlavor::areCommandLineDeveloperToolsAvailable,
        progressTitle,
        true,
        null
      )
    ) {
      return null;
    }

    final HtmlChunk commandChunk = text(getXCodeSelectInstallCommand().getCommandLineString());

    final String message = new HtmlBuilder().append(
      raw(PyBundle.message("python.cldt.required", commandChunk.code()))
    ).toString();

    return new ValidationInfo(message).asWarning().withOKEnabled();
  }
}
