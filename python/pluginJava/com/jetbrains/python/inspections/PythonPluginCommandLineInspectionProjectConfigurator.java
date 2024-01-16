// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.facet.FacetManager;
import com.intellij.ide.CommandLineInspectionProgressReporter;
import com.intellij.ide.CommandLineInspectionProjectConfigurator;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.search.FileTypeIndex;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.intellij.python.community.plugin.java.facet.JavaPythonFacet;
import com.intellij.python.community.plugin.java.facet.JavaPythonFacetType;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public final class PythonPluginCommandLineInspectionProjectConfigurator implements CommandLineInspectionProjectConfigurator {
  @Override
  public @NotNull String getName() {
    return "python";
  }

  @Override
  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription() {
    return PyBundle.message("py.commandline.configure");
  }

  @Override
  public boolean isApplicable(@NotNull ConfiguratorContext context) {
    try {
      try (var stream = Files.walk(context.getProjectPath())) {
        boolean hasAnyPythonFiles = stream.anyMatch(f -> f.toString().endsWith(".py"));

        if (!hasAnyPythonFiles) {
          context.getLogger()
            .reportMessage(3, "Skipping Python interpreter configuration because the project doesn't contain any Python files");
        }

        return hasAnyPythonFiles;
      }
    }
    catch (IOException e) {
      return false;
    }
  }

  @Override
  public void configureEnvironment(@NotNull ConfiguratorContext context) {
    final CommandLineInspectionProgressReporter logger = context.getLogger();
    logger.reportMessage(3, "Python environment configuration...");

    final List<Sdk> sdks = PythonSdkUtil.getAllSdks();
    logSdks(logger, sdks, "Already configured Python interpreters:");

    if (sdks.isEmpty()) {
      logger.reportMessage(3, "No previously configured Python interpreters, detecting...");

      final List<Sdk> detectedSdks = PySdkExtKt.findAllPythonSdks(context.getProjectPath());
      logSdks(logger, detectedSdks, "Python interpreters detected:");

      if (!detectedSdks.isEmpty()) {
        final Sdk detectedSdk = detectedSdks.get(0);
        final Sdk sdk = configureSdk(detectedSdk);
        if (sdk != null) {
          logger.reportMessage(3, "Python interpreter has been configured: " + sdk.getHomePath());
        }
        else {
          logger.reportMessage(1, "Can't configure Python interpreter: " + detectedSdk.getHomePath());
        }
      }
      else {
        logger.reportMessage(1, "Can't find Python interpreter");
      }
    }
  }

  private static void logSdks(@NotNull CommandLineInspectionProgressReporter logger, @NotNull List<Sdk> sdks, @NotNull String prefix) {
    logger.reportMessage(3, prefix);
    for (Sdk sdk : sdks) {
      logger.reportMessage(3, sdk.getHomePath());
    }
  }

  private static @Nullable Sdk configureSdk(@NotNull Sdk detectedSdk) {
    final Sdk sdk = detectedSdk instanceof PyDetectedSdk
                    ? PySdkExtKt.setup((PyDetectedSdk)detectedSdk, Arrays.asList(ProjectJdkTable.getInstance().getAllJdks()))
                    : detectedSdk;

    if (sdk != null) {
      invokeLaterOnWriteThreadUnderLock(() -> ProjectJdkTable.getInstance().addJdk(sdk));
    }

    return sdk;
  }

  @Override
  public void configureProject(@NotNull Project project, @NotNull ConfiguratorContext context) {
    final CommandLineInspectionProgressReporter logger = context.getLogger();

    if (PythonSdkUtil.getAllSdks().isEmpty()) {
      logger.reportMessage(1, "No configured python interpreters");
      return;
    }

    final JavaPythonFacetType facetType = JavaPythonFacetType.getInstance();
    int skippedModules = 0;
    for (Module m : ModuleManager.getInstance(project).getModules()) {
      if (ReadAction.compute(() -> !FileTypeIndex.containsFileOfType(PythonFileType.INSTANCE, m.getModuleContentScope()))) {
        skippedModules++;
        continue;
      }

      final FacetManager facetManager = FacetManager.getInstance(m);

      final var facet = facetManager.getFacetByType(facetType.getId());
      if (facet == null) {
        logger.reportMessage(3, "Setting Python facet for: " + m.getName());

        invokeLaterOnWriteThreadUnderLock(
          () -> {
            final JavaPythonFacet addedFacet = facetManager.addFacet(facetType, facetType.getPresentableName(), null);
            PySdkExtKt.excludeInnerVirtualEnv(m, addedFacet.getConfiguration().getSdk());
          }
        );
      }
      else {
        logger.reportMessage(3, "Python facet already here: " + m.getName());
      }
    }

    logger.reportMessage(
      3,
      "Skipped Python interpreter configuration for " + skippedModules + " module(s) because they don't contain any Python files"
    );
  }

  private static void invokeLaterOnWriteThreadUnderLock(@NotNull Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
    application.invokeLaterOnWriteThread(() -> application.runWriteAction(runnable));
  }
}
